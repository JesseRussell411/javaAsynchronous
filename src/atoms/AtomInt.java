package atoms;


import functionPlus.NotNull;

public class AtomInt extends AtomRef<Integer> {
    public AtomInt(Integer value) {
        super(value, NotNull::check);
    }

    public AtomInt() {
        this(0);
    }

    public Integer addAndGet(int value) {
        if (value == 0) {
            return get();
        } else {
            return modAndGet(thisValue -> thisValue + value);
        }
    }

    public Integer getAndAdd(int value) {
        if (value == 0) {
            return get();
        } else {
            return getAndMod(thisValue -> thisValue + value);
        }
    }

    public Integer incrementAndGet() {
        return addAndGet(1);
    }

    public Integer decrementAndGet() {
        return addAndGet(-1);
    }

    public Integer getAndIncrement() {
        return getAndAdd(1);
    }

    public Integer getAndDecrement() {
        return getAndAdd(-1);
    }
}
