package atoms;


public class AtomBool extends AtomRef<Boolean> {
    public AtomBool(Boolean value) {
        super(value);
    }

    public AtomBool() {
        this(false);
    }

    public Boolean toggleAndGet() {
        Boolean newValue;
        try {
            writeLock.lock();
            newValue = value = !value;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return newValue;
    }

    public Boolean getAndToggle() {
        Boolean newValue, result;
        try {
            writeLock.lock();
            result = value;
            newValue = value = !value;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return result;
    }
}
