package timing;

import java.time.Duration;

public class Timing {

    public static Duration toDuration(long milliseconds, long nanoseconds){
        return Duration.ofMillis(milliseconds).plus(Duration.ofNanos(nanoseconds));
    }
}
