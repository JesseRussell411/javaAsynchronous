package asynchronous.futures;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import asynchronous.TaskCancelException;


/** contains a promise with a public method to cancel */
public class Task<T> implements Future<T> {
	private final Promise<T> _promise;
	Consumer<Boolean> canceler;
	
	public Promise<T> promise() { return _promise; }
	
	public Task(Consumer<Promise<T>.Settle> initializer, Consumer<Boolean> canceler) {
		_promise = new Promise<T>(initializer);
		this.canceler = canceler;
	}
	
	private Task(Promise<T> promise){
		this._promise = promise;
		this.canceler = null;
	}
	
	Task(Consumer<Boolean> canceler) {
		_promise = new Promise<T>();
		this.canceler = canceler;
	}
	
	public Task() {
		_promise = new Promise<T>();
		canceler = null;
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized(_promise) {
			if (_promise.isSettled())
				return false;
			else {
				if (canceler != null)
					canceler.accept(mayInterruptIfRunning);
				_promise.cancel();
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
	
	public static class threadInit_result<T>{
		public final Task<T> task;
		public final Thread thread;
		public threadInit_result(Task<T> task, Thread thread) {
			this.task = task;
			this.thread = thread;
		}
	}
	
	public static <T> threadInit_result<T> threadInit(Consumer<Promise<T>.Settle> initializer){
		final var promiseAndThread = Promise.<T>threadInit(initializer);
		final var task = new Task<T>(promiseAndThread.promise);
		final var thread = promiseAndThread.thread;
		task.canceler = (interruptWhileRunning) -> {
			if (interruptWhileRunning)
				thread.interrupt();
		};
		
		return new threadInit_result<T>(task, thread);
	}
	
	public static <T> Task<T> asyncGet(Supplier<T> func){
		final var task = new Task<T>();
		final var thread = new Thread(() -> {
			try {
				task._promise.resolve(func.get());
			}
			catch(Throwable e) {
				task._promise.reject(e);
			}
		});
		task.canceler = (interruptIfRunning) -> {
			if (interruptIfRunning)
				thread.interrupt();
		};
		thread.start();
		return task;
	}
	
	public static Task<Void> asyncRun(Runnable func){
		return asyncGet(() -> {
			func.run();
			return null;
		});
	}
}
