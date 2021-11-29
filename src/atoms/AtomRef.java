package atoms;

import data.ConcurrentHashSet;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * @param <T>
 */
public class AtomRef<T> {
    private volatile T value;
    private Lock writeLock = new ReentrantLock();

    // ====================== checking ====================
    private BiPredicate<T, T> test;
    private Predicate<T> filter;

    private boolean okToSet(T currentValue, T newValue) {
        return filter.test(newValue) &&
                test.test(currentValue, newValue);
    }

    // ===================== constructors =========================
    public AtomRef(T value, Predicate<T> filter, BiPredicate<T, T> test) {
        this.test = test != null ? test : (currentValue, newValue) -> currentValue != newValue;
        this.filter = filter != null ? filter : (newValue) -> true;
        this.value = value;

        if (!this.filter.test(this.value)) {
            throw new IllegalStateException("Initial value"
                    + (this.value == null ? "" : "( " + this.value + ")")
                    + " does not pass through the provided filter.");
        }
    }

    public AtomRef(T value, Predicate<T> filter) {
        this(value, filter, null);
    }

    public AtomRef(T value) {
        this(value, null, null);
    }

    public AtomRef() {
        this(null, null, null);
    }

    public T get() {
        return value;
    }


    // ====================== onChange callbacks stuff =================================
    private Set<Predicate<T>> onChangeActions = new HashSet<>();

    private ConcurrentHashSet<Consumer<T>> async_onChangeActions = new ConcurrentHashSet<>();
    private ConcurrentHashSet<Consumer<T>> async_onChangeOnceActions = new ConcurrentHashSet<>();

    public Runnable onChangeUntil(Predicate<T> action) {
        onChangeActions.add(action);
        return () -> onChangeActions.remove(action);
    }

    public Runnable onChange(Consumer<T> action) {
        return onChangeUntil(newValue -> {
            action.accept(newValue);
            return false;
        });
    }

    public Runnable onChangeOnce(Consumer<T> action) {
        return onChangeUntil(newValue -> {
            action.accept(newValue);
            return true;
        });
    }

    public Runnable asyncOnChange(Consumer<T> action) {
        async_onChangeActions.add(action);
        return () -> async_onChangeActions.getAndRemove(action);
    }

    public Runnable asyncOnChangeOnce(Consumer<T> action) {
        async_onChangeOnceActions.add(action);
        return () -> async_onChangeOnceActions.getAndRemove(action);
    }

    private void applySyncUpdate(T newValue) {
        onChangeActions.removeIf(action -> action.test(newValue));

        synchronized (this) {
            this.notifyAll();
            ;
        }
    }

    private void applyAsyncUpdate(T newValue) {
        // single use actions
        for (final var current : async_onChangeOnceActions) {
            final var action = async_onChangeOnceActions.getAndRemove(current);

            if (action != null) {
                action.accept(newValue);
            }
        }
        // multi use actions
        for (final var action : async_onChangeActions) {
            action.accept(newValue);
        }

    }
    //

    public boolean trySet(T newValue) {
        boolean update = false;

        try {
            writeLock.lock();
            if (okToSet(this.value, newValue)) {
                this.value = newValue;
                update = true;
                applySyncUpdate(newValue);
            }
        } finally {
            writeLock.unlock();
        }

        if (update) {
            applyAsyncUpdate(newValue);
        }

        return update;
    }

    public boolean tryMod(Function<T, T> mod) {
        T newValue;
        boolean update = false;

        try {
            writeLock.lock();
            newValue = mod.apply(value);

            if (okToSet(value, newValue)) {
                update = true;
                applySyncUpdate(newValue);
            }
        } finally {
            writeLock.unlock();
        }

        if (update) {
            applyAsyncUpdate(newValue);
        }

        return update;
    }

    public T modAndGet(Function<T, T> mod) {
        T newValue;
        boolean update = false;

        try {
            writeLock.lock();
            newValue = mod.apply(value);

            if (okToSet(value, newValue)) {
                update = true;
                applySyncUpdate(newValue);
            }
        } finally {
            writeLock.unlock();
        }

        if (update) {
            applyAsyncUpdate(newValue);
        }

        return newValue;
    }

    public T getAndMod(Function<T, T> mod) {
        T newValue, result;
        boolean update = false;

        try {
            writeLock.lock();
            result = value;
            newValue = mod.apply(value);
            if (okToSet(value, newValue)) {
                update = true;
                applySyncUpdate(newValue);
            }
        } finally {
            writeLock.unlock();
        }

        if (update) {
            applyAsyncUpdate(newValue);
        }

        return result;
    }

    public T getAndSet(T newValue) {
        return getAndMod(value -> newValue);
    }

    public T setAndGet(T newValue) {
        trySet(newValue);
        return newValue;
    }

    public void set(T newValue) {
        trySet(newValue);
    }

    public void mod(Function<T, T> mod) {
        tryMod(mod);
    }

    // ================= fancy stuff ===================
    public class Observer implements AutoCloseable{
        private final Set<Runnable> removers = new ConcurrentHashSet<>();

        private Observer(){}

        public Runnable onChangeUntil(Predicate<T> action) {
            final var remover = AtomRef.this.onChangeUntil(action);
            removers.add(remover);
            return () ->{
                removers.remove(remover);
                remover.run();
            };
        }

        public Runnable onChange(Consumer<T> action) {
            return onChangeUntil(newValue -> {
                action.accept(newValue);
                return false;
            });
        }

        public Runnable onChangeOnce(Consumer<T> action) {
            return onChangeUntil(newValue -> {
                action.accept(newValue);
                return true;
            });
        }

        public Runnable asyncOnChange(Consumer<T> action) {
            final var remover = AtomRef.this.asyncOnChange(action);
            removers.add(remover);
            return () ->{
                removers.remove(remover);
                remover.run();
            };
        }

        public Runnable asyncOnChangeOnce(Consumer<T> action) {
            final var remover = AtomRef.this.asyncOnChangeOnce(action);
            removers.add(remover);
            return () ->{
                removers.remove(remover);
                remover.run();
            };
        }

        public AtomRef<T> getAtom(){
            return AtomRef.this;
        }

        @Override
        public void close(){
            for(final var remover : removers){
                remover.run();
            }
        }
    }

    public void observe(Consumer<Observer> action){
        try(final var observer = new Observer()){
            action.accept(observer);
        }
    }

    public Observer getObserver(){
        return new Observer();
    }
}


