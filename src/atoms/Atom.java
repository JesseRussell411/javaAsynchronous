package atoms;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.function.*;

public class Atom<T> {
    private final AtomicReference<T> value;
    private final Deque<Function<T, Boolean>> onSetActions = new ConcurrentLinkedDeque<>();

    public Atom(T initialValue) {
        value = new AtomicReference<>(initialValue);
    }

    public Atom() {
        this(null);
    }

    // ======================== onSet ==========================
    public void onSetApply(Function<T, Boolean> action) {
        onSetActions.add(action);
    }

    public void onSetAccept(Consumer<T> action) {
        onSetActions.add(value -> {
            action.accept(value);
            return false;
        });
    }

    public void onSetGet(Supplier<Boolean> action) {
        onSetActions.add(value -> action.get());
    }

    public void onSetRun(Runnable action) {
        onSetActions.add(value -> {
            action.run();
            return false;
        });
    }

    public void onSet(Function<T, Boolean> action) {
        onSetApply(action);
    }

    public void onSet(Consumer<T> action) {
        onSetAccept(action);
    }

    public void onSet(Supplier<Boolean> action) {
        onSetGet(action);
    }

    public void onSet(Runnable action) {
        onSetRun(action);
    }
    // END onSet

    public T get() {
        return value.get();
    }

    public void pokeSet(T newValue){
        synchronized (value){
            value.set(newValue);
            notifySet(newValue);
        }
    }

    public void set(T newValue){
        if (value.get() != newValue){
            pokeSet(newValue);
        }
    }

    public void poke() {
        synchronized (value) {
            notifySet(value.get());
        }
    }

    public void modApply(Function<T, T> modifier) {
        synchronized (value) {
            pokeSet(modifier.apply(get()));
        }
    }

    public void modAccept(Consumer<T> modifier){
        synchronized (value) {
            modifier.accept(value.get());
            poke();
        }
    }
    public void mod(Function<T, T> modifier){
        modApply(modifier);
    }
    public void mod(Consumer<T> modifier){
        modAccept(modifier);
    }

    private void notifySet(T newValue) {
        onSetActions.removeIf(current -> current.apply(newValue));

        synchronized (this) {
            notifyAll();
        }
    }
}
