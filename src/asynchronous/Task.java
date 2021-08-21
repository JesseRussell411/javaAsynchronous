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
	private boolean canceled = false;
	
	// properties:
	public Promise<T> getPromise() { return promise; }
	public boolean isResolved() { return promise.isResolved(); }
    public boolean isRejected() { return promise.isRejected(); }
    public boolean isFinalized() { return promise.isFinalized(); }
    public boolean isCanceled() { return canceled; }
    public T getResult() { return promise.getResult(); }
    public Exception getException() { return promise.getException(); }
	
    // constructors:
    Task(){
    	promise = new Promise<T>();
    }
	public Task(BiConsumer<Consumer<T>, Consumer<Exception>> initializer, Runnable onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel(onCancel);
	}
	
	public Task(Consumer<Consumer<T>> initializer, Runnable onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel(onCancel);
	}
	
	// everything else:
	public T await() throws UncheckedInterruptedException, Exception { return promise.await(); }
	public T await(long millisecondTimeout) throws UncheckedInterruptedException, Exception { return promise.await(millisecondTimeout); }
	public T await(long millisecondTimeout, int nanoSecondTimeout) throws UncheckedInterruptedException, Exception { return promise.await(millisecondTimeout, nanoSecondTimeout); }
	
	// o------------------------------------------o
    // | then, onError, onComplete, and onCancel: |
    // o------------------------------------------o
    public synchronized <R> Promise<R> then(Function<T, R> func) { return promise.then(func); }
    public synchronized <R> Promise<R> then(Supplier<R> func) { return promise.then(func); }
    public synchronized Promise<T> then(Consumer<T> func) { return promise.then(func); }
    public synchronized Promise<T> then(Runnable func) { return promise.then(func); }
    public synchronized <R> Promise<R> asyncThen(Function<T, Future<R>> func) { return promise.asyncThen(func); }
    public synchronized <R> Promise<R> asyncThen(Supplier<Future<R>> func) { return promise.asyncThen(func); }
    
    
    public synchronized <R> Promise<R> onCatch(Function<Exception, R> func) { return promise.onCatch(func); }
    public synchronized Promise<Void> onCatch(Consumer<Exception> func) { return promise.onCatch(func); }
    public synchronized Promise<Void> onCatch(Runnable func) { return promise.onCatch(func); }
    public synchronized <R> Promise<R> asyncOnCatch(Function<Exception, Future<R>> func) { return promise.asyncOnCatch(func); }
    public synchronized <R> Promise<R> asyncOnCatch(Supplier<Future<R>> func) { return promise.asyncOnCatch(func); }    
    
    public synchronized <R> Promise<R> onFinally(Supplier<R> func) { return promise.onFinally(func); }
    public synchronized Promise<Void> onFinally(Runnable func) { return promise.onFinally(func); }
    public synchronized <R> Promise<R> asyncOnFinally(Supplier<Future<R>> func) { return promise.asyncOnFinally(func); }
    
    public synchronized <R> Promise<R> onCancel(Supplier<R> func){
    	return new Promise<R>((resolve, reject) ->  {
	    	onCatch(() -> {
	    		if (canceled) {
	    			try {
	    				resolve.accept(func.get());
	    			}
	    			catch (Exception e) {
	    				reject.accept(e);
	    			}
	    		}
	    	});
    	});
    }
    public synchronized Promise<Void> onCancel(Runnable func){
    	return onCancel(() -> {
    		func.run();
    		return null;
    	});
    }
    public synchronized <R> Promise<R> asyncOnCancel(Supplier<Future<R>> func){
    	return new Promise<R>((resolve, reject) -> {
    		onCatch(() -> {
    			if (canceled) {
    				try {
    					final var funcProm = Promise.fromFuture(func.get());
    					funcProm.then(r -> { resolve.accept(r); });
    					funcProm.onCatch(e -> { reject.accept(e); });
    				}
    				catch(Exception e) {
    					reject.accept(e);
    				}
    			}
    		});
    	});
    }
    // END then, onError, onCompletion, and onCancel
    
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
    
	@Override
	public boolean isCancelled() {
		return canceled;
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
