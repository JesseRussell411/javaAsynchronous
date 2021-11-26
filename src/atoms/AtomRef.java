package atoms;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * @param <T>
 */
public class AtomRef<T> {
    volatile T value;
    Lock writeLock = new ReentrantLock();

    // ====================== onChange callbacks stuff =================================
//    private Queue<Consumer<T>> onChangeActions = new ConcurrentLinkedQueue<>();
//    private Queue<Consumer<T>> onChangeOnceActions = new ConcurrentLinkedQueue<>();
//    private Queue<Predicate<T>> onChangeUntilActions = new ConcurrentLinkedQueue<>();
    private ConcurrentHashSet<Consumer<T>> onChangeActions = new ConcurrentHashSet<>();
    private ConcurrentHashSet<Consumer<T>> onChangeOnceActions = new ConcurrentHashSet<>();
    private ConcurrentHashSet<Predicate<T>> onChangeUntilActions = new ConcurrentHashSet<>();


    void applyUpdate(T newValue) {
        try {
            // permanent actions
            for (final var action : onChangeActions) {
                action.accept(newValue);
            }
            // single use actions
            {
                for (final var current : onChangeActions) {
                    final var action = onChangeActions.remove(current);
                    if (action == null) {
                        continue;
                    } else {
                        action.accept(newValue);
                    }
                }
            }
            // multi-use actions
            synchronized (onChangeUntilActions) {
                onChangeUntilActions.removeIf(action -> action.test(newValue));
            }
        } finally {
            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    void applyUpdate() {
        applyUpdate(this.value);
    }

    public Runnable onChange(Consumer<T> action) {
        try {
            onChangeActions.add(action);
            return () ->
                    onChangeActions.remove(action);
        } finally {
            onChangeActions.remove(action);
        }
    }

    public Runnable onChangeOnce(Consumer<T> action) {
        try {
            onChangeOnceActions.add(action);
            return () ->
                    onChangeOnceActions.remove(action);
        } finally {
            onChangeOnceActions.remove(action);
        }
    }

    public Runnable onChangeUntil(Predicate<T> action) {
        try {
            onChangeUntilActions.add(action);
            return () ->
                    onChangeUntilActions.remove(action);
        } finally {
            onChangeUntilActions.remove(action);
        }
    }
    //


    public AtomRef(T value) {
        this.value = value;
    }

    public AtomRef() {
        this(null);
    }

    public T get() {
        return value;
    }

    public boolean trySet(T newValue) {
        boolean update;

        try {
            writeLock.lock();
            if (update = (newValue != this.value)) {
                this.value = newValue;
            }
        } finally {
            writeLock.unlock();
        }

        if (update) {
            applyUpdate(newValue);
        }

        return update;
    }

    public T set(T newValue) {
        trySet(newValue);
        return newValue;
    }

    public T modAndGet(Function<T, T> mod) {
        T newValue;
        boolean update;

        try {
            writeLock.lock();
            newValue = mod.apply(value);
            update = newValue != value;
        } finally {
            writeLock.unlock();
        }

        if (update) {
            applyUpdate(newValue);
        }

        return newValue;
    }

    public T getAndMod(Function<T, T> mod) {
        T newValue, result;
        boolean update;

        try {
            writeLock.lock();
            result = value;
            newValue = mod.apply(value);
            update = newValue != value;
        } finally {
            writeLock.unlock();
        }

        if (update) {
            applyUpdate(newValue);
        }

        return result;
    }
}


