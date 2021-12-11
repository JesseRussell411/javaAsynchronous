package atoms;

import data.ConcurrentHashSet;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Observer<T> implements AutoCloseable {
    private final Observable<T> observed;
    private final ConcurrentHashSet<Runnable> removers = new ConcurrentHashSet<>();

    Observer(Observable<T> toObserve) {
        observed = toObserve;
    }

    public Observable<T> getObserved() {
        return observed;
    }

    public Runnable callback(Consumer<T> action) {
        return removers.addAndGet(observed.callback(action));
    }

    public Runnable callbackOnce(Consumer<T> action) {
        return removers.addAndGet(observed.callbackOnce(action));
    }

    public Runnable callbackUntil(Predicate<T> action) {
        return removers.addAndGet(observed.callbackUntil(action));
    }

    public Runnable callbackUntil_sync(Predicate<T> action) {
        return removers.addAndGet(observed.callbackUntil_sync(action));
    }

    public Runnable callback_sync(Consumer<T> action) {
        return removers.addAndGet(observed.callback_sync(action));
    }

    public Runnable callbackOnce_sync(Consumer<T> action) {
        return removers.addAndGet(observed.callbackOnce_sync(action));
    }

    Runnable callbackUntil_async(Predicate<T> action) {
        return removers.addAndGet(observed.callbackUntil_async(action));
    }

    public Runnable callback_async(Consumer<T> action) {
        return removers.addAndGet(observed.callback_async(action));
    }

    public Runnable callbackOnce_async(Consumer<T> action) {
        return removers.addAndGet(observed.callbackOnce_async(action));
    }

    @Override
    public void close() {
        for (final var remover : removers) {
            remover.run();
        }
    }
}