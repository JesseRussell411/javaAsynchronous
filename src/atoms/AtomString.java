package atoms;

import functionPlus.NotNull;

public class AtomString extends AtomRef<String> {
    public AtomString(String value) {
        super(value, NotNull::check, (current, change) -> !current.equals(change));
    }

    public AtomString() {
        this("");
    }

    public String appendAndGet(String other) {
        if (other == null || other.length() == 0) {
            return get();
        } else {
            return modAndGet(thisValue -> thisValue + other);
        }
    }

    public String getAndAppend(String other) {
        if (other == null || other.length() == 0) {
            return get();
        } else {
            return getAndMod(thisValue -> thisValue + other);
        }
    }

    public void append(String other){
        if (other != null || other.length() != 0) {
            mod(thisValue -> thisValue + other);
        }
    }

    public String prependAndGet(String other) {
        if (other == null || other.length() == 0) {
            return get();
        } else {
            return modAndGet(thisValue -> other + thisValue);
        }
    }

    public String getAndPrepend(String other) {
        if (other == null || other.length() == 0) {
            return get();
        } else {
            return getAndMod(thisValue -> other + thisValue);
        }
    }

    public void prepend(String other){
        if (other != null && other.length() != 0){
            mod(thisValue -> other + thisValue);
        }
    }
}
