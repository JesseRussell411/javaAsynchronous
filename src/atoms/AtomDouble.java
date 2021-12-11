package atoms;


import functionPlus.NotNull;

public class AtomDouble extends AtomRef<Double> {
    public AtomDouble(Double value) {
        super(value, NotNull::test);
    }

    public AtomDouble() {
        this(0.0);
    }

    public Double addAndGet(double value) {
        if (value == 0.0) {
            return get();
        } else {
            return modAndGet(thisValue -> thisValue + value);
        }
    }

    public Double getAndAdd(double value) {
        if (value == 0.0) {
            return get();
        } else {
            return getAndMod(thisValue -> thisValue + value);
        }
    }

    public void add(double value){
        if (value != 0.0){
            mod(thisValue -> thisValue + value);
        }
    }

    public Double scaleAndGet(double value) {
        if (value == 1.0) {
            return get();
        } else {
            return modAndGet(thisValue -> thisValue * value);
        }
    }

    public Double getAndScale(double value) {
        if (value == 1.0) {
            return get();
        } else {
            return getAndMod(thisValue -> thisValue * value);
        }
    }

    public void scale(double value){
        if (value != 1.0){
            mod(thisValue -> thisValue * value);
        }
    }
}
