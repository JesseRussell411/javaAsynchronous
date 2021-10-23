package asynchronous.futures;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;


/** contains a task and is resolve or rejected with public methods instead of an initializer. */
public class Deferred<T> implements Future<T> {
	private final Promise<T> _promise;
	private final Promise<T>.Settle _settle;
	private final Task<T> _task;
	
	public Promise<T> promise() { return _promise; }
	public Task<T> task() { return _task; }
	public Promise<T>.Settle settle() { return _settle; }
	
	public Deferred(Consumer<Boolean> canceler) {
		_task = new Task<T>(new Promise<T>(), canceler);
		_promise = _task.promise();
		_settle = _task.settle;
	}
	
	public Deferred() {
		this(null);
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
