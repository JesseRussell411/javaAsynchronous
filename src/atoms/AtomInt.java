package atoms;


public class AtomInt extends AtomRef<Integer> {
    public AtomInt(Integer value) {
        super(value);
    }

    public AtomInt() {
        this(0);
    }

    public Integer addAndGet(int value) {
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

    public Integer getAndAdd(int value) {
        if (value == 0){
            return get();
        }
        try {
            writeLock.lock();
            Integer result = this.value;
            this.value += value;
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    public Integer incrementAndGet(){
        return addAndGet(1);
    }
    public Integer decrementAndGet(){
        return addAndGet(-1);
    }
    public Integer getAndIncrement(){
        return getAndAdd(1);
    }
    public Integer getAndDecrement(){
        return getAndAdd(-1);
    }
}
