package asynchronous;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

public class Deferred<T> implements Future<T>{
	private final Promise<T> promise = new Promise<T>();
	private boolean canceled = false;
	
	public Promise<T> getPromise() { return promise; }
	public boolean isResolved() { return promise.isResolved(); }
    public boolean isRejected() { return promise.isRejected(); }
    public boolean isComplete() { return promise.isComplete(); }
    public T getResult() { return promise.getResult(); }
    public Exception getException() { return promise.getException(); }
	
	public boolean resolve(T result) {
		synchronized(promise) {
			if (promise.isComplete()) {
				return false;
			}
			else {
				promise.resolve(result);
				return true;
			}
		}
	}
	
	public boolean reject(Exception exception) {
		synchronized(promise) {
			if (promise.isComplete()) {
				return false;
			}
			else {
				promise.reject(exception);
				return true;
			}
		}
	}
	
	public boolean resolve(Supplier<T> getResult) {
		synchronized(promise) {
			if (promise.isComplete()) {
				return false;
			}
			else {
				promise.resolve(getResult.get());
				return true;
			}
		}
	}
	
	public boolean reject(Supplier<Exception> getException) {
		synchronized(promise) {
			if (promise.isComplete()) {
				return false;
			}
			else {
				promise.reject(getException.get());
				return true;
			}
		}
	}
	
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
    
    
    public synchronized <R> Promise<R> onError(Function<Exception, R> func) { return promise.onError(func); }
    public synchronized Promise<Void> onError(Consumer<Exception> func) { return promise.onError(func); }
    public synchronized Promise<Void> onError(Runnable func) { return promise.onError(func); }
    public synchronized <R> Promise<R> asyncOnError(Function<Exception, Future<R>> func) { return promise.asyncOnError(func); }
    public synchronized <R> Promise<R> asyncOnError(Supplier<Future<R>> func) { return promise.asyncOnError(func); }    
    
    public synchronized <R> Promise<R> onCompletion(Supplier<R> func) { return promise.onCompletion(func); }
    public synchronized Promise<Void> onCompletion(Runnable func) { return promise.onCompletion(func); }
    public synchronized <R> Promise<R> asyncOnCompletion(Supplier<Future<R>> func) { return promise.asyncOnCompletion(func); }
    
    public synchronized <R> Promise<R> onCancel(Supplier<R> func){
    	return new Promise<R>((resolve, reject) ->  {
	    	onError(() -> {
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
    		onError(() -> {
    			if (canceled) {
    				try {
    					final var funcProm = Promise.fromFuture(func.get());
    					funcProm.then(r -> { resolve.accept(r); });
    					funcProm.onError(e -> { reject.accept(e); });
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
    @Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		promise.reject(new CancellationException());
    	return true;
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
