package atoms;


public class AtomBool extends AtomRef<Boolean> {
    public AtomBool(Boolean value) {
        super(value);
    }

    public AtomBool() {
        this(false);
    }

    public Boolean toggleAndGet() {
        try {
            writeLock.lock();
            return value = !value;
        } finally {
            writeLock.unlock();
        }
    }

    public Boolean getAndToggle() {
        try {
            writeLock.lock();
            Boolean result = value;
            value = !value;
            return result;
        } finally {
            writeLock.unlock();
        }
    }
}
