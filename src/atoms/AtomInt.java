package atoms;


public class AtomInt extends AtomRef<Integer> {
    public AtomInt(Integer value) {
        super(value);
    }

    public AtomInt() {
        this(0);
    }

    public Integer addAndGet(int value) {
        if (value == 0){
            return get();
        }

        Integer newValue;
        try {
            writeLock.lock();
            newValue = this.value += value;
        } finally {
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return newValue;
    }

    public Integer getAndAdd(int value) {
        if (value == 0){
            return get();
        }

        Integer newValue, result;
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
