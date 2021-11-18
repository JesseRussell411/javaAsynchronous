package asynchronous;

import java.util.function.*;

import asynchronous.futures.Promise;

public class Timing {
	public static Promise<Void> setTimeout(Runnable func, long milliseconds, int nanoseconds){
		final var promiseAndThread = Promise.<Void>threadInit(settle -> {
			try {
				Thread.sleep(milliseconds, nanoseconds);
				func.run();
				settle.resolve();
			}
			catch(Throwable e) {
				settle.reject(e);
			}
		});
		
		return promiseAndThread.promise;
	}
	
	public static <T> Promise<T> setTimeout(Supplier<T> func, long milliseconds, int nanoseconds){
		final var promiseAndThread = Promise.<T>threadInit(settle -> {
			try {
				Thread.sleep(milliseconds, nanoseconds);
				settle.resolve(func.get());
			}
			catch(Throwable e) {
				settle.reject(e);
			}
		});
		
		return promiseAndThread.promise;
	}
	
	public static Promise<Void> setTimeout(Runnable func, long sleepForMilliseconds) {
		return setTimeout(func, sleepForMilliseconds, 0);
	}
	
	public static <T> Promise<T> setTimeout(Supplier<T> func, long sleepForMilliseconds) {
		return setTimeout(func, sleepForMilliseconds, 0);
	}

	public static Promise<Void> setTimeout(long sleepForMilliseconds) {
		return setTimeout(() -> null, sleepForMilliseconds, 0);
	}
}
