package asynchronous.futures;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

import asynchronous.TaskCancelException;


/** contains a task and is resolve or rejected with public methods instead of an initializer. */
public class Deferred<T> implements Future<T> {
	private final Promise<T> _promise;
	private final Task<T> _task;
	
	public Promise<T> promise() { return _promise; }
	public Task<T> task() { return _task; }
	
	public Deferred(Consumer<Boolean> canceler) {
		_task = new Task<T>(canceler);
		_promise = _task.promise();
	}
	
	public Deferred() {
		_task = new Task<T>(null);
		_promise = _task.promise();
	}
	
	public boolean resolve(T result) {
		return _promise.resolve(result);
	}
	public boolean reject(Throwable error) {
		return _promise.reject(error);
	}
	
	public boolean resolveFrom(Supplier<T> resultGetter) {
		return _promise.resolveFrom(resultGetter);
	}
	
	public boolean rejectFrom(Supplier<Throwable> errorGetter) {
		return _promise.rejectFrom(errorGetter);
	}
	
	public boolean cancel() {
		return _promise.cancel();
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return _task.cancel(mayInterruptIfRunning);
	}
	@Override
	public boolean isCancelled() {
		return _task.isCancelled();
	}
	@Override
	public boolean isDone() {
		return _task.isDone();
	}
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return _promise.get();
	}
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return _promise.get(timeout, unit);
	}
}
