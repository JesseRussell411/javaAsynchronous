package asynchronous;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

/** A promise that can be canceled */
public class Task<T> implements Future<T>{
	// fields:
	public final Promise<T> promise;
	boolean isCancelled;
	
    @Override
    public boolean isCancelled() { return isCancelled; }
    public T getResult() { return promise.getResult(); }
    public Throwable getException() { return promise.getError(); }
	
    // constructors:
    Task(){
    	promise = new Promise<T>();
    }
    Task(Promise<T> promise){
    	this.promise = promise;
    }
	public Task(BiConsumer<Consumer<T>, Consumer<Throwable>> initializer, Consumer<CancellationException> onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel(onCancel);
	}
	
	public Task(Consumer<Consumer<T>> initializer, Consumer<CancellationException> onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel(onCancel);
	}
	
	// everything else:
    
    // onCancel:
    public static class IllegalCancelledException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		private Task<?> task;
    	public Task<?> getTask() { return task; }
    	public IllegalCancelledException(Task<?> task) {
    		this.task = task;
    	}
    }
    
    public synchronized <R> Promise<R> onCancelApply(Function<CancellationException, R> func){
    	return new Promise<R>((resolve, reject) -> {
    		onCatch(e -> {
    			if (isCancelled()) {
    				if (e instanceof CancellationException) {
	    				try {
	    					resolve.accept(func.apply((CancellationException)e));
	    				}
	    				catch(Throwable e2) {
	    					reject.accept(e);
	    				}
    				}
    				else {
    					throw new IllegalCancelledException(this);
    				}
    			}
    		});
    	});
    }
    public synchronized <R> Promise<R> onCancel(Function<CancellationException, R> func){
    	return onCancelApply(func);
    }
    public synchronized Promise<Void> onCancelAccept(Consumer<CancellationException> func){
    	return onCancelApply(e -> {
    		func.accept(e);
    		return null;
    	});
    }
    public synchronized Promise<Void> onCancel(Consumer<CancellationException> func){
    	return onCancelAccept(func);
    }
    public synchronized <R> Promise<R> onCancelGet(Supplier<R> func){
    	return onCancelApply(e ->{
    		return func.get();
    	});
    }
    public synchronized <R> Promise<R> onCancel(Supplier<R> func){
    	return onCancelGet(func);
    }
    public synchronized Promise<Void> onCancelRun(Runnable func){
    	return onCancelApply(e -> {
    		func.run();
    		return null;
    	});
    }
    public synchronized Promise<Void> onCancel(Runnable func){
    	return onCancelRun(func);
    }
    public synchronized <R> Promise<R> asyncOnCancelApply(Function<CancellationException, Future<R>> func){
    	return new Promise<R>((resolve, reject) -> {
    		onCatch(e -> {
    			if (isCancelled()) {
    				if (e instanceof CancellationException) {
    					try {
	    					final var funcPromise = Promise.fromFuture(func.apply((CancellationException)e));
	    					funcPromise.then(r -> {resolve.accept(r);});
	    					funcPromise.onCatch(e2 -> {reject.accept(e);});
    					}
    					catch(Throwable e2) {
    						reject.accept(e2);
    					}
    				}
    				else {
    					throw new IllegalCancelledException(this);
    				}
    			}
    		});
    	});
    }
    public synchronized <R> Promise<R> asyncOnCancel(Function<CancellationException, Future<R>> func){
    	return asyncOnCancelApply(func);
    }
    public synchronized <R> Promise<R> asyncOnCancelGet(Supplier<Future<R>> func){
    	return asyncOnCancelApply(ce -> {
    		return func.get();
    	});
    }
    public synchronized <R> Promise<R> asyncOnCancel(Supplier<Future<R>> func){
    	return asyncOnCancelGet(func);
    }
    // END then, onError, onCompletion, and onCancel
    
    public static <T> Task<T> threadInit(BiConsumer<Consumer<T>, Consumer<Throwable>> initializer, Consumer<CancellationException> onCancel){
    	final var task = new Task<T>();
    	task.onCancel(onCancel);
    	
    	final var thread = new Thread(() -> {
    		initializer.accept(t -> task.promise.resolve(t), e -> task.promise.reject(e));
    	});
    	thread.start();
    	return task;
    }
    public static <T> Task<T> threadInit(Consumer<Consumer<T>> initializer, Consumer<CancellationException> onCancel){
    	final var task = new Task<T>();
    	task.onCancel(onCancel);
    	
    	final var thread = new Thread(() -> {
    		initializer.accept(t -> task.promise.resolve(t));
    	});
    	thread.start();
    	return task;
    }
    public static <T> Task<T> threadInit(Supplier<T> initializer, Consumer<CancellationException> onCancel){
    	final var promise = Promise.<T>threadInit(initializer);
    	final var task = new Task<T>(promise);
    	task.onCancel(onCancel);
    	return task;
    }
    public static Task<Void> threadInit(Runnable initializer, Consumer<CancellationException> onCancel){
    	final var promise = Promise.threadInit(initializer);
    	final var task = new Task<Void>(promise);
    	task.onCancel(onCancel);
    	return task;
    }
    public static <T> Task<T> fromFuture(Future<T> future){
    	final var task = new Task<T>(Promise.fromFuture(future));
    	task.onCancel(() -> {
    		future.cancel(mayInterruptIfRunning_DEFAULT);
    	});
    	return task;
    }
    
    // o-----------------------o
    // | Interface Compliance: |
    // o-----------------------o
    public synchronized boolean cancel(String reason, boolean mayInterruptIfRunning) {
    	if (isFinalized()) {
    		return false;
    	}
    	else {
    		isCancelled = true;
			promise.reject(new CancellationException(reason));
    		return true;
    	}
    }
    
    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    	if (isFinalized()) {
    		return false;
    	}
    	else {
			promise.reject(new CancellationException());
    		return true;
    	}
    }
    private static final boolean mayInterruptIfRunning_DEFAULT = true;
    public synchronized boolean cancel(String reason) {
    	return cancel(reason, mayInterruptIfRunning_DEFAULT);
    }
    
    public synchronized boolean cancel() {
    	return cancel(mayInterruptIfRunning_DEFAULT);
    }
	/** Added for interface implementation. Equivalent to isComplete. */
	@Override
	public synchronized boolean isDone() {
		return promise.isDone();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public synchronized T get() throws InterruptedException, ExecutionException {
		return promise.get();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public synchronized T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return promise.get(timeout, unit);
	}
}
