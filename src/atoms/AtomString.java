package atoms;

public class AtomString extends AtomRef<String> {
    public AtomString(String value){
        super(value, (a, b) -> a.equals(b));
    }
    public AtomString(){
        this("");
    }

    public String appendAndGet(String other){
        if (other.length() == 0){
            return get();
        }

        String newValue;
        try{
            writeLock.lock();
            newValue = value += other;
        } finally{
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return newValue;
    }

    public String getAndAppend(String other){
        if (other.length() == 0){
            return get();
        }

        String newValue, result;
        try{
            writeLock.lock();
            result = value;
            newValue = value += other;
        } finally{
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return result;
    }

    public String prependAndGet(String other){
        if (other.length() == 0){
            return get();
        }

        String newValue;
        try{
            writeLock.lock();
            newValue = value += other;
        } finally{
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return newValue;
    }

    public String getAndPrepend(String other){
        if (other.length() == 0){
            return get();
        }

        String newValue, result;
        try{
            writeLock.lock();
            result = value;
            newValue = value += other;
        } finally{
            writeLock.unlock();
        }

        applyUpdate(newValue);
        return result;
    }
}
