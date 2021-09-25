package asynchronous.futures;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

import asynchronous.TaskCancelException;

/** contains a promise with a public method to cancel */
public class Task<T> implements Future<T>{
	public final Promise<T> promise;
	public BiConsumer<TaskCancelException, Boolean> onCancel;
	
	public Task(BiConsumer<Function<T, Boolean>, Function<Throwable, Boolean>> initializer, BiConsumer<TaskCancelException, Boolean> onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel = onCancel;
	}
	public Task(Consumer<Function<T, Boolean>> initializer, BiConsumer<TaskCancelException, Boolean> onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel = onCancel;
	}
	
	Task(BiConsumer<TaskCancelException, Boolean> onCancel){
		promise = new Promise<T>();
		this.onCancel = onCancel;
	}
	
	Task(){
		promise = new Promise<T>();
		onCancel = (e, b) -> {};
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized(promise) {
			if (promise.isSettled())
				return false;
			else {
				final var cancelError = new TaskCancelException(this);
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
}
