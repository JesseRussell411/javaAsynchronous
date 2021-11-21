package atoms;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.function.*;

public class AtomLong {
    private final AtomicLong value;
    private final Deque<Function<Long, Boolean>> onSetActions = new ConcurrentLinkedDeque<>();

    public AtomLong(long initialValue) {
        value = new AtomicLong(initialValue);
    }

    public AtomLong() {
        this(0);
    }

    // ======================== onSet ==========================
    public void onSetApply(Function<Long, Boolean> action) {
        onSetActions.add(action);
    }

    public void onSetAccept(Consumer<Long> action) {
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

    public void onSet(Function<Long, Boolean> action) {
        onSetApply(action);
    }

    public void onSet(Consumer<Long> action) {
        onSetAccept(action);
    }

    public void onSet(Supplier<Boolean> action) {
        onSetGet(action);
    }

    public void onSet(Runnable action) {
        onSetRun(action);
    }
    // END onSet

    public long get() {
        return value.get();
    }

    public void pokeSet(long newValue) {
        synchronized (value) {
            value.set(newValue);
            notifySet(newValue);
        }
    }

    public void set(long newValue) {
        if (value.get() != newValue) {
            pokeSet(newValue);
        }
    }

    public void poke() {
        synchronized (value) {
            notifySet(value.get());
        }
    }

    public void modApply(LongUnaryOperator modifier) {
        synchronized (value) {
            pokeSet(modifier.applyAsLong(get()));
        }
    }

    public void modAccept(LongConsumer modifier){
        synchronized (value) {
            modifier.accept(value.get());
            poke();
        }
    }
    public void mod(LongUnaryOperator modifier){
        modApply(modifier);
    }
    public void mod(LongConsumer modifier){
        modAccept(modifier);
    }

    public long getAndIncrement(){
        synchronized (value) {
            final var result = value.get();
            notifySet(value.incrementAndGet());
            return result;
        }
    }

    public long incrementAndGet(){
        synchronized (value) {
            final var result = value.incrementAndGet();
            notifySet(result);
            return result;
        }
    }

    public long getAndDecrement(){
        synchronized (value) {
            final var result = value.get();
            notifySet(value.decrementAndGet());
            return result;
        }
    }

    public long decrementAndGet(){
        synchronized (value) {
            final var result = value.decrementAndGet();
            notifySet(result);
            return result;
        }
    }

    public long addAndGet(long value){
        synchronized (this.value){
            final var result = this.value.addAndGet(value);
            notifySet(result);
            return result;
        }
    }

    public long getAndAdd(long value){
        synchronized (this.value){
            final var result = this.value.get();
            notifySet(this.value.addAndGet(value));
            return result;
        }
    }

    private void notifySet(long newValue) {
        onSetActions.removeIf(current -> current.apply(newValue));

        synchronized (this) {
            notifyAll();
        }
    }
}
