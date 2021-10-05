package asynchronous.futures;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import asynchronous.TaskCancelException;


/** contains a promise with a public method to cancel */
public class Task<T> implements Future<T>{
	public final Promise<T> promise;
	BiConsumer<TaskCancelException, Boolean> onCancel;
	
	public Task(Consumer<Promise<T>.Settle> initializer, BiConsumer<TaskCancelException, Boolean> onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel = onCancel;
	}
	
	private Task(Promise<T> promise){
		this.promise = promise;
	}
	
	Task(BiConsumer<TaskCancelException, Boolean> onCancel) {
		promise = new Promise<T>();
		this.onCancel = onCancel;
	}
	
	private Task() {
		promise = new Promise<T>();
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized(promise) {
			if (promise.isSettled())
				return false;
			else {
				final var cancelError = new TaskCancelException(this);
				if (onCancel != null)
					onCancel.accept(cancelError, mayInterruptIfRunning);
				promise.reject(cancelError);
				return true;
			}
		}
	}
	@Override
	public boolean isCancelled() {
		return promise.getError() instanceof TaskCancelException tce && tce.getTask() == this;
	}
	@Override
	public boolean isDone() {
		return promise.isDone();
	}
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return promise.get();
	}
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return promise.get(timeout, unit);
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
		task.onCancel = (taskCancelException, interruptWhileRunning) -> {
			if (interruptWhileRunning)
				thread.interrupt();
		};
		
		return new threadInit_result<T>(task, thread);
	}
	
	public static <T> Task<T> asyncGet(Supplier<T> func){
		final var task = new Task<T>();
		final var thread = new Thread(() -> {
			try {
				task.promise.resolve(func.get());
			}
			catch(Throwable e) {
				task.promise.reject(e);
			}
		});
		task.onCancel = (exception, interruptIfRunning) -> thread.interrupt();
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
