package asynchronous.futures;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

import asynchronous.TaskCancelException;


/** contains a task and is resolve or rejected with public methods instead of an initializer. */
public class Deferred<T> implements Future<T> {
	public final Promise<T> promise;
	public final Task<T> task;
	
	public Deferred(BiConsumer<TaskCancelException, Boolean> onCancel) {
		task = new Task<T>(onCancel);
		promise = task.promise;
	}
	
	public Deferred() {
		task = new Task<T>((tce, ciir) -> {});
		promise = task.promise;
	}
	
	public boolean resolve(T result) {
		return promise.resolve(result);
	}
	public boolean reject(Throwable error) {
		return promise.reject(error);
	}
	
	public boolean resolveFrom(Supplier<T> resultGetter) {
		return promise.resolveFrom(resultGetter);
	}
	
	public boolean rejectFrom(Supplier<Throwable> errorGetter) {
		return promise.rejectFrom(errorGetter);
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return task.cancel(mayInterruptIfRunning);
	}
	@Override
	public boolean isCancelled() {
		return task.isCancelled();
	}
	@Override
	public boolean isDone() {
		return task.isDone();
	}
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return promise.get();
	}
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return promise.get(timeout, unit);
	}
}
