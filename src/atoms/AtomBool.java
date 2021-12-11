package atoms;


import functionPlus.NotNull;

public class AtomBool extends AtomRef<Boolean> {
    public AtomBool(Boolean value) {
        super(value, NotNull::test);
    }

    public AtomBool() {
        this(false);
    }

    public Boolean notAndGet() {
        return modAndGet(value -> !value);
    }

    public Boolean getAndNot() {
        return getAndMod(value -> !value);
    }

    public void not() {
        mod(value -> !value);
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

    public void and(boolean other) {
        if (other == false) {
            set(false);
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

    public void or(boolean other) {
        if (other == true) {
            set(true);
        }
    }

    public Boolean xorAndGet(boolean other) {
        return modAndGet(value -> value ^ other);
    }

    public Boolean getAndXor(boolean other) {
        return getAndMod(value -> value ^ other);
    }

    public void xor(boolean other) {
        mod(value -> value ^ other);
    }
}
