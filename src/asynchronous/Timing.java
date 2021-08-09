package asynchronous;

import java.util.function.*;

public class Timing {
	public static void setTimeout(Runnable func, long sleepForMilliseconds, int sleepForNanoseconds) {
		new Thread(() -> {
			try {
				Thread.sleep(sleepForMilliseconds, sleepForNanoseconds);
			} catch(InterruptedException e) {}
			
			func.run();
		}).start();
	}
	
	public static <T> Promise<T> setTimeout(Supplier<T> func, long sleepForMilliseconds, int sleepForNanoseconds) {
		return new Promise<T>((resolve, reject) -> new Thread(() -> {
			try {
				Thread.sleep(sleepForMilliseconds, sleepForNanoseconds);
				resolve.accept(func.get());
			}
			catch(Exception e) {
				reject.accept(e);
			}
		}).start());
	}
	
	public static void setTimeout(Runnable func, long sleepForMilliseconds) {
		setTimeout(func, sleepForMilliseconds, 0);
	}
	
	public static <T> Promise<T> setTimeout(Supplier<T> func, long sleepForMilliseconds) {
		return setTimeout(func, sleepForMilliseconds, 0);
	}
}
