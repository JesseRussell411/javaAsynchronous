package asynchronous;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;


/** A task which is resolve or rejected with public methods instead of an initializer. */
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
