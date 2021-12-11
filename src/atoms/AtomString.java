package atoms;

import functionPlus.NotNull;

public class AtomString extends AtomRef<String> {
    public AtomString(String value) {
        super(value, NotNull::test, (current, change) -> !current.equals(change));
    }

    public AtomString() {
        this("");
    }

    public String appendAndGet(String other) {
        if (other != null && other.length() != 0){
            return modAndGet(thisValue -> thisValue + other);
        } else {
            return get();
        }
    }

    public String getAndAppend(String other) {
        if (other != null && other.length() != 0){
            return getAndMod(thisValue -> thisValue + other);
        } else {
            return get();
        }
    }

    public void append(String other){
        if (other != null && other.length() != 0) {
            mod(thisValue -> thisValue + other);
        }
    }

    public String prependAndGet(String other) {
        if (other != null && other.length() != 0){
            return modAndGet(thisValue -> other + thisValue);
        } else {
            return get();
        }
    }

    public String getAndPrepend(String other) {
        if (other != null && other.length() != 0){
            return getAndMod(thisValue -> other + thisValue);
        } else {
            return get();
        }
    }

    public void prepend(String other){
        if (other != null && other.length() != 0){
            mod(thisValue -> other + thisValue);
        }
    }
}
