package atom;

import observation.Observable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Atom<T> extends Observable<Atom<T>.OldAndNew> {
    private final AtomicReference<T> value = new AtomicReference<>();

    public Atom(T initialValue) {
        value.set(initialValue);
    }

    public class OldAndNew {
        public final T oldValue;
        public final T newValue;

        private OldAndNew(T oldValue, T newValue) {
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    public T get(){
        return value.get();
    }

    public OldAndNew set(T value) {
        final var oldValue = this.value.get();

        synchronized (this.value) {
            this.value.set(value);
        }

        final var oAndN = new OldAndNew(oldValue, value);
        update(oAndN);
        return oAndN;
    }

    public OldAndNew mod(Function<T, T> mod) {
        final var oldValue = this.value.get();
        T newValue;

        synchronized (this.value) {
            newValue = mod.apply(oldValue);
            this.value.set(newValue);
        }

        final var oAndN = new OldAndNew(oldValue, newValue);
        update(oAndN);
        return oAndN;
    }
}
