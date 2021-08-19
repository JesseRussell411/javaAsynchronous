package asynchronous;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

public class Deferred<T> implements Future<T>{
	private final Promise<T> promise;
	
	private Deferred(Promise<T> promise) {
		this.promise = promise;
	}
	
	public Deferred(BiConsumer<Consumer<T>, Consumer<Exception>> initializer) {
		this(new Promise<T>(initializer));
	}

	public Deferred(Consumer<Consumer<T>> initializer) {
		this(new Promise<T>(initializer));
	}
	
	public Deferred() {
		this(new Promise<T>());
	}
	
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
	
	// o----------------------------o
    // | Then, Error, and Complete: |
    // o----------------------------o
	public synchronized <R> Promise<R> asyncThen(Function<T, Future<R>> func) { return promise.asyncThen(func); }
	public synchronized <R> Promise<R> asyncThen(Supplier<Future<R>> func) { return promise.asyncThen(func); }
    public synchronized <R> Promise<R> then(Function<T, R> func) { return promise.then(func); }
    public synchronized <R> Promise<R> then(Supplier<R> func) { return promise.then(func); }
    public synchronized Promise<T> then(Consumer<T> func) { return promise.then(func); }
    public synchronized Promise<T> then(Runnable func) { return promise.then(func); }
    
    public synchronized <R> Promise<R> asyncOnError(Function<Exception, Future<R>> func) { return promise.asyncOnError(func); }
    public synchronized <R> Promise<R> asyncOnError(Supplier<Future<R>> func) { return promise.asyncOnError(func); }    
    public synchronized <R> Promise<R> onError(Function<Exception, R> func) { return promise.onError(func); }
    public synchronized Promise<Void> onError(Consumer<Exception> func) { return promise.onError(func); }
    public synchronized Promise<Void> onError(Runnable func) { return promise.onError(func); }
    
    public synchronized <R> Promise<R> asyncOnCompletion(Supplier<Future<R>> func) { return promise.asyncOnCompletion(func); }
    public synchronized <R> Promise<R> onCompletion(Supplier<R> func) { return promise.onCompletion(func); }
    public synchronized Promise<Void> onCompletion(Runnable func) { return promise.onCompletion(func); }
    // END Then and Error
    
    // o-------------------o
    // | Static Factories: |
    // o-------------------o
    public static <T> Deferred<T> threadInit(BiConsumer<Consumer<T>, Consumer<Exception>> initializer){
    	return new Deferred<T>(Promise.threadInit(initializer));
    }
    public static <T> Deferred<T> threadInit(Consumer<Consumer<T>> initializer){
    	return new Deferred<T>(Promise.threadInit(initializer));
    }
    
    // o-----------------------o
    // | Interface Compliance: |
    // o-----------------------o
    /** Only included for interface implementation. Deferred cannot be canceled. Will always return false. */
    @Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return promise.cancel(mayInterruptIfRunning);
	}
    /** Only included for interface implementation. Deferred cannot be canceled. Will always return false. */
	@Override
	public boolean isCancelled() {
		return promise.isCancelled();
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
