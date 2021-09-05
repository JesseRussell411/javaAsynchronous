package asynchronous;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.*;

/** A task which is resolve or rejected with public methods instead of an initializer. */
public class Deferred<T> implements Future<T> {
	// fields:
	private final Task<T> task;
	
	// properties:
	public Task<T> getTask() { return task; }
	public Promise<T> getPromise() { return task.promise; }
	public boolean isResolved() { return task.isResolved(); }
    public boolean isRejected() { return task.isRejected(); }
    public boolean isFinalized() { return task.isFinalized(); }
    @Override
	public boolean isCancelled() { return task.isCancelled(); }
    public T getResult() { return task.getResult(); }
    public Throwable getException() { return task.getException(); }
    
    // constructors:
    Deferred(Task<T> task){
    	this.task = task;
    }
	public Deferred() {
		task = new Task<T>();
	}
	public Deferred(Runnable onCancel) {
		task = new Task<T>();
		task.onCancel(onCancel);
	}
	
	
	// public resolve and reject:
	public synchronized boolean resolve(T result) {
		if (task.isFinalized()) {
			return false;
		}
		else {
			task.promise.resolve(result);
			return true;
		}
	}
	
	public synchronized boolean reject(Throwable exception) {
		if (task.isFinalized()) {
			return false;
		}
		else {
			task.promise.reject(exception);
			return true;
		}
	}
	
	public synchronized boolean resolveUsing(Supplier<T> getResult) {
		if (task.isFinalized()) {
			return false;
		}
		else {
			task.promise.resolve(getResult.get());
			return true;
		}
	}
	
	public synchronized boolean rejectUsing(Supplier<Throwable> getException) {
		if (task.isFinalized()) {
			return false;
		}
		else {
			task.promise.reject(getException.get());
			return true;
		}
	}

	
	// everything else:
	public T await() throws UncheckedInterruptedException, Throwable { return task.await(); }
	public T await(long millisecondTimeout) throws UncheckedInterruptedException, Throwable { return task.await(millisecondTimeout); }
	public T await(long millisecondTimeout, int nanoSecondTimeout) throws UncheckedInterruptedException, Throwable { return task.await(millisecondTimeout, nanoSecondTimeout); }
	
	// o------------------------------------------o
    // | then, onError, onComplete, and onCancel: |
    // o------------------------------------------o
    public synchronized <R> Promise<R> then(Function<T, R> func) { return task.then(func); }
    public synchronized <R> Promise<R> then(Supplier<R> func) { return task.then(func); }
    public synchronized Promise<T> then(Consumer<T> func) { return task.then(func); }
    public synchronized Promise<T> then(Runnable func) { return task.then(func); }
    public synchronized <R> Promise<R> asyncThen(Function<T, Future<R>> func) { return task.asyncThen(func); }
    public synchronized <R> Promise<R> asyncThen(Supplier<Future<R>> func) { return task.asyncThen(func); }
    
    public synchronized <R> Promise<R> onCatch(Function<Throwable, R> func) { return task.onCatch(func); }
    public synchronized Promise<Void> onCatch(Consumer<Throwable> func) { return task.onCatch(func); }
    public synchronized Promise<Void> onCatch(Runnable func) { return task.onCatch(func); }
    public synchronized <R> Promise<R> asyncOnCatch(Function<Throwable, Future<R>> func) { return task.asyncOnCatch(func); }
    public synchronized <R> Promise<R> asyncOnCatch(Supplier<Future<R>> func) { return task.asyncOnCatch(func); }    
    
    public synchronized <R> Promise<R> onFinally(Supplier<R> func) { return task.onFinally(func); }
    public synchronized Promise<Void> onFinally(Runnable func) { return task.onFinally(func); }
    public synchronized <R> Promise<R> asyncOnFinally(Supplier<Future<R>> func) { return task.asyncOnFinally(func); }
    
    public synchronized <R> Promise<R> onCancel(Function<CancellationException, R> func){ return task.onCancel(func);}
    public synchronized Promise<Void> onCancel(Consumer<CancellationException> func){ return task.onCancel(func); }
    public synchronized <R> Promise<R> onCancel(Supplier<R> func){ return task.onCancel(func); }
    public synchronized Promise<Void> onCancel(Runnable func){ return task.onCancel(func); }
    public synchronized <R> Promise<R> asyncOnCancel(Function<CancellationException, Future<R>> func){ return task.asyncOnCancel(func); }
    public synchronized <R> Promise<R> asyncOnCancel(Supplier<Future<R>> func){ return task.asyncOnCancel(func); }
    // END then, onError, onCompletion, and onCancel
    
    // o-----------------------o
    // | Interface Compliance: |
    // o-----------------------o
    @Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return task.cancel(mayInterruptIfRunning);
	}
    public boolean cancel(String reason, boolean mayInterruptIfRunning) {
    	return task.cancel(reason, mayInterruptIfRunning);
    }
    public boolean cancel(String reason) {
    	return task.cancel(reason);
    }
    public boolean cancel() {
    	return task.cancel();
    }
	
	/** Added for interface implementation. Equivalent to isComplete. */
	@Override
	public boolean isDone() {
		return task.isDone();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return task.get();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return task.get(timeout, unit);
	}
}
