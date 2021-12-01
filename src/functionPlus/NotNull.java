package functionPlus;

public class NotNull {
    /**
     * @param value
     * @return Whether the value is not null.
     */
    public static boolean test(Object value){
        return value != null;
    }

    /**
     * @param value
     * @return true if the value is not null
     * @throws NullPointerException If the value is null.
     */
    public static boolean check(Object value) {
        if (value == null) throw new NullPointerException();
        return true;
    }
}
