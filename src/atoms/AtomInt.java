package atoms;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.function.*;

public class AtomInt {
    private final AtomicInteger value;
    private final Deque<Function<Integer, Boolean>> onSetActions = new ConcurrentLinkedDeque<>();

    public AtomInt(int initialValue) {
        value = new AtomicInteger(initialValue);
    }

    public AtomInt() {
        this(0);
    }

    // ======================== onSet ==========================
    public void onSetApply(Function<Integer, Boolean> action) {
        onSetActions.add(action);
    }

    public void onSetAccept(Consumer<Integer> action) {
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

    public void onSet(Function<Integer, Boolean> action) {
        onSetApply(action);
    }

    public void onSet(Consumer<Integer> action) {
        onSetAccept(action);
    }

    public void onSet(Supplier<Boolean> action) {
        onSetGet(action);
    }

    public void onSet(Runnable action) {
        onSetRun(action);
    }
    // END onSet

    public int get() {
        return value.get();
    }

    public void pokeSet(int newValue) {
        synchronized (value) {
            value.set(newValue);
            notifySet(newValue);
        }
    }

    public void set(int newValue) {
        if (value.get() != newValue) {
            pokeSet(newValue);
        }
    }

    public void poke() {
        synchronized (value) {
            notifySet(value.get());
        }
    }

    public void modApply(IntUnaryOperator modifier) {
        synchronized (value) {
            pokeSet(modifier.applyAsInt(get()));
        }
    }

    public void modAccept(IntConsumer modifier){
        synchronized (value) {
            modifier.accept(value.get());
            poke();
        }
    }
    public void mod(IntUnaryOperator modifier){
        modApply(modifier);
    }
    public void mod(IntConsumer modifier){
        modAccept(modifier);
    }

    public int getAndIncrement(){
        synchronized (value) {
            final var result = value.get();
            notifySet(value.incrementAndGet());
            return result;
        }
    }

    public int incrementAndGet(){
        synchronized (value) {
            final var result = value.incrementAndGet();
            notifySet(result);
            return result;
        }
    }

    public int getAndDecrement(){
        synchronized (value) {
            final var result = value.get();
            notifySet(value.decrementAndGet());
            return result;
        }
    }

    public int decrementAndGet(){
        synchronized (value) {
            final var result = value.decrementAndGet();
            notifySet(result);
            return result;
        }
    }

    public int addAndGet(int value){
        synchronized (this.value){
            final var result = this.value.addAndGet(value);
            notifySet(result);
            return result;
        }
    }

    public int getAndAdd(int value){
        synchronized (this.value){
            final var result = this.value.get();
            notifySet(this.value.addAndGet(value));
            return result;
        }
    }

    private void notifySet(int newValue) {
        onSetActions.removeIf(current -> current.apply(newValue));

        synchronized (this) {
            notifyAll();
        }
    }
}
