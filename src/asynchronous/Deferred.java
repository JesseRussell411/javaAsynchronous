package asynchronous;
import java.util.function.*;

public class Deferred<T> {
	private final Promise<T> promise;
	
	public Deferred(BiConsumer<Consumer<T>, Consumer<Exception>> initializer) {
		promise = new Promise<T>(initializer);
	}

	public Deferred(Consumer<Consumer<T>> initializer) {
		promise = new Promise<T>(initializer);
	}
	
	public Deferred() {
		promise = new Promise<T>();
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
	
	public T await() throws InterruptedException { return promise.await(); }
	
	// o----------------------------o
    // | Then, Error, and Complete: |
    // o----------------------------o
	public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> func) { return promise.asyncThen(func); }
	public synchronized <R> Promise<R> asyncThen(Supplier<Promise<R>> func) { return promise.asyncThen(func); }
    public synchronized <R> Promise<R> then(Function<T, R> func) { return promise.then(func); }
    public synchronized <R> Promise<R> then(Supplier<R> func) { return promise.then(func); }
    public synchronized Promise<T> then(Consumer<T> func) { return promise.then(func); }
    public synchronized Promise<T> then(Runnable func) { return promise.then(func); }
    
    public synchronized <R> Promise<R> asyncError(Function<Exception, Promise<R>> func) { return promise.asyncError(func); }
    public synchronized <R> Promise<R> asyncError(Supplier<Promise<R>> func) { return promise.asyncError(func); }    
    public synchronized Promise<T> error(Consumer<Exception> func) { return promise.error(func); }
    public synchronized Promise<T> error(Runnable func) { return promise.error(func); }
    
    public synchronized <R> Promise<R> asyncComplete(Supplier<Promise<R>> func) { return promise.asyncComplete(func); }
    public synchronized <R> Promise<R> complete(Supplier<R> func) { return promise.complete(func); }
    public synchronized Promise<Object> complete(Runnable func) { return promise.complete(func); }
    // END Then and Error
}
