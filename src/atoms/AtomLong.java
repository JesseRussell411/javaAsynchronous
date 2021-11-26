package atoms;


public class AtomLong extends AtomRef<Long> {
    public AtomLong(Long value) {
        super(value);
    }

    public AtomLong() {
        this(0L);
    }

    public Long addAndGet(long value) {
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

    public Long getAndAdd(long value) {
        if (value == 0){
            return get();
        }
        try {
            writeLock.lock();
            Long result = this.value;
            this.value += value;
            return result;
        } finally {
            writeLock.unlock();
        }
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
