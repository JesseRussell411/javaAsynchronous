package asynchronous.futures;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;


/** contains a promise with a public method to cancel */
public class Task<T> implements Future<T> {
	private final Promise<T> _promise;
	final Promise<T>.Settle settle;
	Consumer<Boolean> canceler;
	
	public Promise<T> promise() { return _promise; }
	
	public Task(Consumer<Promise<T>.Settle> initializer, Consumer<Boolean> canceler) {
		_promise = new Promise<T>(initializer);
		settle = _promise.new Settle();
		this.canceler = canceler;
	}

	public Task(Consumer<Boolean> canceler) {
		_promise = new Promise<T>();
		settle = _promise.new Settle();
		this.canceler = canceler;
	}
	
	public Task() {
		this(null);
	}
	
	Task(Promise<T> promise, Consumer<Boolean> canceler){
		_promise = promise;
		settle = promise.new Settle();
		this.canceler = canceler;
	}
	
	
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized(_promise) {
			if (_promise.isSettled())
				return false;
			else {
				if (canceler != null)
					canceler.accept(mayInterruptIfRunning);
				settle.cancel();
				return true;
			}
		}
	}
	@Override
	public boolean isCancelled() {
		return _promise.isCanceled();
	}
	@Override
	public boolean isDone() {
		return _promise.isDone();
	}
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return _promise.get();
	}
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return _promise.get(timeout, unit);
	}
	
	public static class TaskAndThread<T>{
		public final Task<T> task;
		public final Thread thread;
		public TaskAndThread(Task<T> task, Thread thread) {
			this.task = task;
			this.thread = thread;
		}
	}
	
	public static <T> TaskAndThread<T> threadInit(Consumer<Promise<T>.Settle> initializer){
		final var promiseAndThread = Promise.<T>threadInit(initializer);
		final var task = new Task<T>(promiseAndThread.promise, null);
		final var thread = promiseAndThread.thread;
		task.canceler = (interruptWhileRunning) -> {
			if (interruptWhileRunning)
				thread.interrupt();
		};
		
		return new TaskAndThread<T>(task, thread);
	}
}
