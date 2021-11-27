package atoms;


public class AtomLong extends AtomRef<Long> {
    public AtomLong(Long value) {
        super(value);
    }

    public AtomLong() {
        this(0L);
    }

    public Long addAndGet(long value) {
        if (value == 0){
            return get();
        }

        Long newValue;
        try {
            writeLock.lock();
            newValue = this.value += value;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return newValue;
    }

    public Long getAndAdd(long value) {
        if (value == 0){
            return get();
        }

        Long newValue, result;
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

    public Long incrementAndGet(){
        return addAndGet(1);
    }
    public Long decrementAndGet(){
        return addAndGet(-1);
    }
    public Long getAndIncrement(){
        return getAndAdd(1);
    }
    public Long getAndDecrement(){
        return getAndAdd(-1);
    }
}
