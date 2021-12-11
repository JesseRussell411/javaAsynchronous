package atoms;

import asynchronous.futures.Deferred;
import asynchronous.futures.Promise;
import data.ConcurrentHashSet;

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
public class AtomRef<T> implements Atom<T>{
    private volatile T value;
    private final Lock writeLock = new ReentrantLock();
    private final Observable<T> observable;
    private final Consumer<T> update;

    // ====================== checking ====================
    private final BiPredicate<T, T> test;
    private final Predicate<T> filter;

    private boolean okToSet(T currentValue, T newValue) {
        return filter.test(newValue) &&
                test.test(currentValue, newValue);
    }

    // ===================== constructors =========================
    public AtomRef(T value, Predicate<T> filter, BiPredicate<T, T> test) {
        this.test = test != null ? test : (currentValue, newValue) -> currentValue != newValue;
        this.filter = filter != null ? filter : (newValue) -> true;
        this.value = value;
        final var observable_create_result = Observable.<T>create();
        observable = observable_create_result.observable;
        update = observable_create_result.update;

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

    public Promise<T> getNext() {
        return observable.getNext();
    }

    public Observable<T> onChange() { return observable; }

    public boolean trySet(T newValue) {
        boolean update = false;

        try {
            writeLock.lock();
            if (okToSet(this.value, newValue)) {
                this.value = newValue;
            } else {
                return false;
            }
        } finally {
            writeLock.unlock();
        }

        this.update.accept(newValue);
        return true;
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

    public boolean tryMod(Function<T, T> mod) {
        T newValue;
        boolean update = false;

        try {
            writeLock.lock();
            newValue = mod.apply(value);

            if (okToSet(value, newValue)) {
                this.value = newValue;
            } else {
                return false;
            }
        } finally {
            writeLock.unlock();
        }

        this.update.accept(newValue);
        return true;
    }

    public T modAndGet(Function<T, T> mod) {
        T newValue;
        boolean update = false;

        try {
            writeLock.lock();
            newValue = mod.apply(value);

            if (okToSet(value, newValue)) {
                value = newValue;
            } else {
                return value;
            }
        } finally {
            writeLock.unlock();
        }

        this.update.accept(newValue);
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
                this.value = newValue;
            } else{
                return result;
            }
        } finally {
            writeLock.unlock();
        }

        this.update.accept(newValue);
        return result;
    }

    public void mod(Function<T, T> mod) {
        tryMod(mod);
    }
}


