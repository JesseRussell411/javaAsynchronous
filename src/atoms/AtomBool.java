package atoms;


import functionPlus.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class AtomBool extends AtomRef<Boolean> {
    public AtomBool(Boolean value) {
        super(value, NotNull::check);
    }

    public AtomBool() {
        this(false);
    }

    public Boolean toggleAndGet() {
        return modAndGet(value -> !value);
    }

    public Boolean getAndToggle() {
        return getAndMod(value -> !value);
    }

    public Boolean andAndGet(boolean other) {
        if (other == true) {
            return get();
        } else {
            return modAndGet(value -> value && other);
        }
    }

    public Boolean getAndAnd(boolean other) {
        if (other == true) {
            return get();
        } else {
            return getAndMod(value -> value && other);
        }
    }

    public Boolean orAndGet(boolean other) {
        if (other == false) {
            return get();
        } else {
            return modAndGet(value -> value || other);
        }
    }

    public Boolean getAndOr(boolean other) {
        if (other == false) {
            return get();
        } else {
            return getAndMod(value -> value || other);
        }
    }

    public Boolean xorAndGet(boolean other) {
        return modAndGet(value -> value ^ other);
    }

    public boolean getAndXor(boolean other) {
        return getAndMod(value -> value ^ other);
    }
}
