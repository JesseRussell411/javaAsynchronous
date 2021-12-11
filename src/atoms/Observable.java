package atoms;

import asynchronous.futures.Deferred;
import asynchronous.futures.Promise;
import data.ConcurrentHashSet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Observable<T> {
    private final Map<Predicate<T>, Boolean> callbacks_sync = new ConcurrentHashMap<>();
    private final Map<Predicate<T>, Boolean> callbacks_async = new ConcurrentHashMap<>();
    private final ConcurrentHashSet<Consumer<T>> callbacks_async_once = new ConcurrentHashSet<>();
    private volatile Deferred<T> nextValue = new Deferred<>();

    private Observable(){}
    public static class Result_create<T>{
        public final Observable<T> observable;
        public final Consumer<T> update;
        private Result_create(Observable<T> observable, Consumer<T> update){
            this.observable = observable;
            this.update = update;
        }
    }

    public static <T> Result_create<T> create(){
        final var observable = new Observable<T>();
        final var result = new Result_create<T>(observable, observable::update);
        return result;
    }

    private void update(T value){
        applyUpdate_sync(value);
        applyUpdate_async(value);
        nextValue.settle().resolve(value);
        nextValue = new Deferred<>();

        synchronized (this) {
            notifyAll();
        }
    }

    private void applyUpdate_sync(T newValue) {
        callbacks_sync.keySet().removeIf(action -> {
            synchronized (action){
                if (callbacks_sync.get(action) && action.test(newValue)) {
                    callbacks_sync.replace(action, false);
                    return true;
                } else return false;
            }
        });
    }

    private void applyUpdate_async(T newValue) {
        // single use actions (guarantied to run once only)
        for (final var current : callbacks_async_once) {
            final var action = callbacks_async_once.getAndRemove(current);

            if (action != null) {
                action.accept(newValue);
            }
        }

        // multi use actions
        callbacks_async.keySet().removeIf(action -> {
            if (callbacks_async.get(action) && action.test(newValue)){
                callbacks_async.replace(action, false);
                return true;
            } else return false;
        });
    }

    // ======== public facing stuff =========
    public Promise<T> getNext(){
        return nextValue.promise();
    }

    public Runnable callback(Consumer<T> action) {
        return callback_async(action);
    }

    public Runnable callbackOnce(Consumer<T> action) {
        return callbackOnce_async(action);
    }

    public Runnable callbackUntil(Predicate<T> action) {
        return callbackUntil_sync(action);
    }

    public Runnable callbackUntil_sync(Predicate<T> action) {
        callbacks_sync.put(action, true);
        return () -> callbacks_sync.remove(action);
    }

    public Runnable callback_sync(Consumer<T> action) {
        return callbackUntil_sync(newValue -> {
            action.accept(newValue);
            return false;
        });
    }

    public Runnable callbackOnce_sync(Consumer<T> action) {
        return callbackUntil_sync(newValue -> {
            action.accept(newValue);
            return true;
        });
    }

    Runnable callbackUntil_async(Predicate<T> action) {
        callbacks_async.put(action, true);
        return () -> callbacks_async.remove(action);
    }

    public Runnable callback_async(Consumer<T> action) {
        return callbackUntil_async(value -> {
            action.accept(value);
            return false;
        });
    }

    public Runnable callbackOnce_async(Consumer<T> action) {
        callbacks_async_once.add(action);
        return () -> callbacks_async_once.remove(action);
    }

    // ================== get observer ========================

    public Observer<T> observe() {
        return new Observer<T>(this);
    }

    public void observe(Consumer<Observer<T>> action) {
        try (final var observer = observe()) {
            action.accept(observer);
        }
    }

    public <R> R observe(Function<Observer<T>, R> action) {
        try (final var observer = observe()) {
            return action.apply(observer);
        }
    }
}
