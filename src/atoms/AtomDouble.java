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
        try {
            writeLock.lock();
            return this.value + value;
        } finally {
            writeLock.unlock();
        }
    }

    public Double getAndAdd(double value) {
        if (value == 0) {
            return get();
        }
        try {
            writeLock.lock();
            Double result = this.value;
            this.value += value;
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    public Double scaleAndGet(double scalar) {
        if (scalar == 1) {
            return get();
        }
        try {
            writeLock.lock();
            return value *= scalar;
        } finally {
            writeLock.unlock();
        }
    }

    public Double getAndScale(double scalar) {
        if (scalar == 1) {
            return get();
        }
        try {
            writeLock.lock();
            Double result = value;
            value *= scalar;
            return result;
        } finally {
            writeLock.unlock();
        }
    }
}
