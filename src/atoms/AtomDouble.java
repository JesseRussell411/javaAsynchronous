package atoms;


public class AtomDouble extends AtomRef<Double> {
    public AtomDouble(Double value) {
        super(value);
    }

    public AtomDouble() {
        this(0.0);
    }

    public Double addAndGet(double value) {
        if (value == 0) {
            return get();
        }

        Double newValue, result;
        try {
            writeLock.lock();
            result = this.value;
            newValue = this.value += value;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return result;
    }

    public Double getAndAdd(double value) {
        if (value == 0) {
            return get();
        }

        Double newValue, result;
        try {
            writeLock.lock();
            result = this.value;
            newValue = this.value += value;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return result;
    }

    public Double scaleAndGet(double scalar) {
        if (scalar == 1) {
            return get();
        }

        Double newValue;
        try {
            writeLock.lock();
            newValue = value *= scalar;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return newValue;
    }

    public Double getAndScale(double scalar) {
        if (scalar == 1) {
            return get();
        }

        Double newValue, result;
        try {
            writeLock.lock();
            result = value;
            newValue = value *= scalar;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return result;
    }
}
