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
	Promise<T> promise;
	
	// properties:
	public Promise<T> getPromise() { return promise; }
	public boolean isResolved() { return promise.isResolved(); }
    public boolean isRejected() { return promise.isRejected(); }
    public boolean isFinalized() { return promise.isFinalized(); }
    @Override
    public boolean isCancelled() { return promise.isRejected() && promise.getException() instanceof CancellationException; }
    public T getResult() { return promise.getResult(); }
    public Throwable getException() { return promise.getException(); }
	
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
	public T await() throws UncheckedInterruptedException, Throwable { return promise.await(); }
	public T await(long millisecondTimeout) throws UncheckedInterruptedException, Throwable { return promise.await(millisecondTimeout); }
	public T await(long millisecondTimeout, int nanoSecondTimeout) throws UncheckedInterruptedException, Throwable { return promise.await(millisecondTimeout, nanoSecondTimeout); }
	
	// o------------------------------------------o
    // | then, onError, onComplete, and onCancel: |
    // o------------------------------------------o
    public synchronized <R> Promise<R> then(Function<T, R> func) { return promise.then(func); }
    public synchronized <R> Promise<R> then(Supplier<R> func) { return promise.then(func); }
    public synchronized Promise<T> then(Consumer<T> func) { return promise.then(func); }
    public synchronized Promise<T> then(Runnable func) { return promise.then(func); }
    public synchronized <R> Promise<R> asyncThen(Function<T, Future<R>> func) { return promise.asyncThen(func); }
    public synchronized <R> Promise<R> asyncThen(Supplier<Future<R>> func) { return promise.asyncThen(func); }
    
    
    public synchronized <R> Promise<R> onCatch(Function<Throwable, R> func) { return promise.onCatch(func); }
    public synchronized Promise<Void> onCatch(Consumer<Throwable> func) { return promise.onCatch(func); }
    public synchronized Promise<Void> onCatch(Runnable func) { return promise.onCatch(func); }
    public synchronized <R> Promise<R> asyncOnCatch(Function<Throwable, Future<R>> func) { return promise.asyncOnCatch(func); }
    public synchronized <R> Promise<R> asyncOnCatch(Supplier<Future<R>> func) { return promise.asyncOnCatch(func); }    
    
    public synchronized <R> Promise<R> onFinally(Supplier<R> func) { return promise.onFinally(func); }
    public synchronized Promise<Void> onFinally(Runnable func) { return promise.onFinally(func); }
    public synchronized <R> Promise<R> asyncOnFinally(Supplier<Future<R>> func) { return promise.asyncOnFinally(func); }
    
    private static final IllegalStateException wrongCancelException =
    		new IllegalStateException("A canceled task was rejected with an exception other than CancellationException.");
    
    public synchronized <R> Promise<R> onCancel(Function<CancellationException, R> func){
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
    					throw new Error(wrongCancelException);
    				}
    			}
    		});
    	});
    }
    public synchronized Promise<Void> onCancel(Consumer<CancellationException> func){
    	return onCancel(e -> {
    		func.accept(e);
    		return null;
    	});
    }
    public synchronized <R> Promise<R> onCancel(Supplier<R> func){
    	return onCancel(e ->{
    		return func.get();
    	});
    }
    public synchronized Promise<Void> onCancel(Runnable func){
    	return onCancel(e -> {
    		func.run();
    		return null;
    	});
    }
    public synchronized <R> Promise<R> asyncOnCancel(Function<CancellationException, Future<R>> func){
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
    					throw new Error(wrongCancelException);
    				}
    			}
    		});
    	});
    }
    public synchronized <R> Promise<R> asyncOnCancel(Supplier<Future<R>> func){
    	return asyncOnCancel(ce -> {
    		return func.get();
    	});
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
    public boolean cancel(String reason, boolean mayInterruptIfRunning) {
    	if (isFinalized()) {
    		return false;
    	}
    	else {
			promise.reject(new CancellationException(reason));
    		return true;
    	}
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
    	if (isFinalized()) {
    		return false;
    	}
    	else {
			promise.reject(new CancellationException());
    		return true;
    	}
    }
    private static final boolean mayInterruptIfRunning_DEFAULT = true;
    public boolean cancel(String reason) {
    	return cancel(reason, mayInterruptIfRunning_DEFAULT);
    }
    
    public boolean cancel() {
    	return cancel(mayInterruptIfRunning_DEFAULT);
    }
	/** Added for interface implementation. Equivalent to isComplete. */
	@Override
	public boolean isDone() {
		return promise.isDone();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return promise.get();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return promise.get(timeout, unit);
	}
}
