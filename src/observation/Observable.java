package observation;

import collections.ConcurrentHashSet;

import java.util.function.Consumer;
import java.util.function.Function;

public class Observable<T> {
    private final ConcurrentHashSet<Observer<T>> observers = new ConcurrentHashSet<>();
    private final Observer<T> defaultObserver = new Observer<T>(this);

    void addObserver(Observer observer) {
        observers.add(observer);
    }

    void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    protected void update(T value) {
        for (final var observer : observers) {
            observer.update(value);
        }
    }

    public static class NewObservable<T> {
        private final Observable<T> self = new Observable<>();

        public void update(T value) {
            self.update(value);
        }

        public Observable<T> get() {
            return self;
        }
    }

    public static <T> NewObservable<T> create() {
        return new NewObservable<T>();
    }

    // ===== observing the observable ======
    public Observer<T> observe(){
        return defaultObserver;
    }

    public AutoClosableObserver<T> tempObserve(){
        return new AutoClosableObserver<>(this);
    }

    public void tempObserve(Consumer<AutoClosableObserver<T>> observation){
        try(final var observer = tempObserve()){
            observation.accept(observer);
        }
    }

    public <R> R tempObserve(Function<AutoClosableObserver<T>, R> observation){
        try(final var observer = tempObserve()){
            return observation.apply(observer);
        }
    }
}
