package atoms;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.function.*;

public class AtomBool{
    private final AtomicBoolean value;
    private final Deque<Function<Boolean, Boolean>> onSetActions = new ConcurrentLinkedDeque<>();

    public AtomBool(boolean initialValue){
        value = new AtomicBoolean(initialValue);
    }
    public AtomBool(){
        this(false);
    }

    // ======================== onSet ==========================
    public void onSetApply(Function<Boolean, Boolean> action) {
        onSetActions.add(action);
    }

    public void onSetAccept(Consumer<Boolean> action) {
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

    public void onSet(Function<Boolean, Boolean> action) {
        onSetApply(action);
    }

    public void onSet(Consumer<Boolean> action) {
        onSetAccept(action);
    }

    public void onSet(Supplier<Boolean> action) {
        onSetGet(action);
    }

    public void onSet(Runnable action) {
        onSetRun(action);
    }
    // END onSet

    public boolean get(){
        return value.get();
    }

    public void pokeSet(boolean newValue){
        synchronized (value){
            value.set(newValue);
            notifySet(newValue);
        }
    }

    public void set(boolean newValue){
        if (value.get() != newValue){
            pokeSet(newValue);
        }
    }

    public void poke(){
        synchronized (value){
            notifySet(value.get());
        }
    }

    public void mod(Function<Boolean, Boolean> modifier){
        synchronized (value){
            set(modifier.apply(get()));
        }
    }

    public void toggle(){
        synchronized (value){
            set(!get());
        }
    }

    private void notifySet(boolean newValue){
        onSetActions.removeIf(current -> current.apply(newValue));

        synchronized (this){
            notifyAll();
        }
    }
}
