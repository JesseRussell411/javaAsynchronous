package asynchronous;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;


/** contains a task and is resolve or rejected with public methods instead of an initializer. */
public class Deferred<T> implements Future<T> {
	public final Promise<T> promise;
	public final Task<T> task;
	
	public Deferred(BiConsumer<TaskCancelException, Boolean> onCancel) {
		task = new Task<T>(onCancel);
		promise = task.promise;
	}
	public Deferred() {
		task = new Task<T>();
		promise = task.promise;
	}
	
	public boolean resolve(T result) {
		return promise.resolve(result);
	}
	public boolean reject(Throwable error) {
		return promise.reject(error);
	}
	
	public boolean resolveFrom(Supplier<T> resultGetter) {
		synchronized(promise) {
			if (promise.isSettled()) {
				return false;
			}
			else {
				return promise.resolve(resultGetter.get());
			}
		}
	}
	
	public boolean rejectFrom(Supplier<Throwable> errorGetter) {
		synchronized(promise) {
			if (promise.isSettled()) {
				return false;
			}
			else {
				return promise.reject(errorGetter.get());
			}
		}
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
