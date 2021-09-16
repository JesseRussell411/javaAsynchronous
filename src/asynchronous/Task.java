package asynchronous;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

/** contains a promise and a method to cancel */
public class Task<T> implements Future<T>{
	public final Promise<T> promise;
	
	public Task(BiConsumer<Function<T, Boolean>, Function<Throwable, Boolean>> initializer) {
		promise = new Promise<T>(initializer);
	}
	public Task(Consumer<Function<T, Boolean>> initializer) {
		promise = new Promise<T>(initializer);
	}
	
	
}
