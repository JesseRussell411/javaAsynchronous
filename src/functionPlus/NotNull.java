package functionPlus;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class NotNull {
    public static boolean test(Object value){
        return value != null;
    }
    public static boolean check(Object value) {
        if (value == null) throw new NullPointerException();
        return true;
    }
}
