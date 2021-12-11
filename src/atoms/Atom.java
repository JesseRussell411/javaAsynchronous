package atoms;

import asynchronous.futures.Promise;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Atom<T> {
    public T get();

    public Promise<T> getNext();

    public Observable<T> onChange();

    public boolean trySet(T newValue);

    public T getAndSet(T newValue);

    public T setAndGet(T newValue);

    public void set(T newValue);

    public boolean tryMod(Function<T, T> mod);

    public T modAndGet(Function<T, T> mod);

    public T getAndMod(Function<T, T> mod);

    public void mod(Function<T, T> mod);
}
