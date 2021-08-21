package asynchronous;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import reference.*;
/**
 *
 * @author jesse
 */
public class Promise<T> implements Future<T> {
	// queue of functions to run on resolution (like the try block in try/catch)
    private final Queue<Consumer<T>> thenQueue = new LinkedList<>();
    private final Queue<Promise<?>> thenPromises = new LinkedList<>();
    // queue of functions to run on rejection (like the catch block)
    private final Queue<Consumer<Exception>> catchQueue = new LinkedList<>();
    private boolean resolved = false;
    private boolean rejected = false;
    private T result = null;
    private Exception exception = null;
    
    // Whether the promise has been finalized (resolved or rejected);
    public boolean isFinalized() { return resolved || rejected; }
    // Whether the promise has been resolved.
    public boolean isResolved() { return resolved; }
    // Whether the promise has been rejected.
    public boolean isRejected() { return rejected; }
    
    /**
     *  Returns result of the promise. If the promise has not been resolved: returns null.
     *  NOTE: even if the promise has been resolved this will still return null if the result was null. To check if the promise has resolved use "isResolved()".
     */
    public T getResult() { return result; }
    
    /**
     * Returns the exception with which the promise was rejected. If the promise has not been rejected returns null.
     * NOTE: even if the promise has been rejected this will still return null if the exception was null. To check if the promise has been rejected use "isRejected()".
     * @return
     */
    public Exception getException() { return exception; }
    
    Promise(){}
    
    /**
     * Runs the initializer to create a promise. NOTE: The initializer will block the calling function as the initializer is called in the same thread.
     * To initialize asynchronously use "Promise.threadInit" instead or start a new thread inside the initializer.
     * @param Initializer function that consumes resolve and reject functions. Use them to resolve or reject the promise respectively.
     */
    public Promise(BiConsumer<Consumer<T>, Consumer<Exception>> initializer) {
        initializer.accept((r) -> resolve(r), (e) -> reject(e));
    }

    /**
     * Runs the initializer to create a promise. NOTE: The initializer will block the calling function as the initializer is called in the same thread.
     * To initialize asynchronously use "Promise.threadInit" instead or start a new thread inside the initializer.
     * @param Initializer function that consumes a resolve method. Use it to resolve the promise.
     */
    public Promise(Consumer<Consumer<T>> initializer){
        initializer.accept((t) -> resolve(t));
    }
    
    
    
    private synchronized <E> void runConsumerQueue(Queue<Consumer<E>> queue, E input) {
    	Consumer<E> func;
    	while((func = queue.poll()) != null) {
    		func.accept(input);
    	}
    }
    
    private synchronized void runRunnableQueue(Queue<Runnable> queue) {
    	Runnable func;
    	while((func = queue.poll()) != null) {
    		func.run();
    	}
    }
    
    private synchronized void handleThen(){
    	// run then queue
    	runConsumerQueue(thenQueue, result);
    	
    	// clear then promises
    	thenPromises.clear();
    	
    	// clear the error queue
    	catchQueue.clear();
    }
    
    private synchronized void handleError() {
    	// run error queue
    	runConsumerQueue(catchQueue, exception);
    	
    	// reject all then promises
    	Promise<?> prom;
    	while((prom = thenPromises.poll()) != null) {
    		prom.reject(exception);
    	}
    	
    	// remove all thens
    	thenQueue.clear();
    }
    
    private synchronized void handleCompletionIfComplete() {
    	if (rejected) {
    		handleError();
    	}
    	else if (resolved) {
    		handleThen();
    	}
    }
    
    synchronized void resolve(T result) throws ResolutionOfCompletedPromiseException{
    	if (resolved || rejected) {
    		throw new ResolutionOfCompletedPromiseException(this);
    	}
    	
        // take the result
        this.result = result;
        
        // raise resolved
        resolved = true;
        
        // run then and complete
        handleCompletionIfComplete();
        
        // notify threads stuck in the await function that the wait is over.
        notifyAll();
    }
    
    synchronized void reject(Exception exception) throws RejectionOfCompletedPromiseException{
    	if (resolved || rejected) {
    		throw new RejectionOfCompletedPromiseException(this);
    	}
    	
        // take the result
        this.exception = exception;
        
        // raise rejected
        rejected = true;
        
        // run error and complete
        handleCompletionIfComplete();
        
        // notify threads stuck in the await function that the wait is over.
        notifyAll();
    }
    
    public synchronized T await() throws UncheckedInterruptedException, Exception {
    	notify();
    	try {
	    	while(!resolved && !rejected) {
	    		wait();
	    	}
    	}
    	catch (InterruptedException ie) {
    		throw new UncheckedInterruptedException(ie);
    	}
    	
    	if (resolved) {
    		return result;
    	}
    	else {
    		throw exception;
    	}
    }
    
    public synchronized T await(long millisecondTimeout, int nanoSecondTimeout) throws UncheckedInterruptedException, TimeoutException, Exception {
    	AtomicBoolean timedOut = new AtomicBoolean(false);
    	
    	Timing.setTimeout(() ->{
    		timedOut.set(true);
    		notify();
    	}, millisecondTimeout, nanoSecondTimeout);
    	
    	notify();
    	try {
	    	while(!(resolved || rejected || timedOut.get())) {
	    		wait();
	    	}
    	}
    	catch (InterruptedException ie) {
    		throw new UncheckedInterruptedException(ie);
    	}
    	
    	
    	if (timedOut.get()) {
    		throw new TimeoutException();
    	}
    	else if (resolved) {
    		return result;
    	}
    	else {
    		throw exception;
    	}
    }
    
    public synchronized T await(long milliseconedTimeout) throws UncheckedInterruptedException, Exception {
    	return await(milliseconedTimeout, 0);
    }

    // o----------------------------------o
    // | Then, onError, and onCompletion: |
    // o----------------------------------o
    public synchronized <R> Promise<R> then(Function<T, R> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		thenQueue.add(r -> {
    			try {
	    			final var r2 = func.apply(r);
	    			resolve.accept(r2);
    			}
    			catch(Exception e) {
    				reject.accept(e);
    			}
    		});
    	});
    	thenPromises.add(prom);
    	
    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncThen(Function<T, Future<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		thenQueue.add(r -> {
    			try {
	    			final var funcProm = Promise.fromFuture(func.apply(r));
	    			funcProm.then(r2 -> {resolve.accept(r2);});
	    			funcProm.onCatch(e -> {reject.accept(e);});
    			}
    			catch (Exception e) {
    				reject.accept(e);
    			}
    		});
    	});
    	thenPromises.add(prom);

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> onCatch(Function<Exception, R> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		catchQueue.add(e -> {
    			try {
    				resolve.accept(func.apply(e));
    			}
    			catch(Exception e2) {
    				reject.accept(e2);
    			}
    		});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncOnCatch(Function<Exception, Future<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		catchQueue.add(e -> {
    			try {
	    			final var funcProm = Promise.fromFuture(func.apply(e));
	    			funcProm.then(r -> {resolve.accept(r);});
	    			funcProm.onCatch(e2 -> {reject.accept(e2);});
    			}
    			catch(Exception e2) {
    				reject.accept(e2);
    			}
    		});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> onFinally(Supplier<R> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		Runnable fullFunc = () -> {
    			try {
	    			resolve.accept(func.get());
    			}
    			catch(Exception e) {
    				reject.accept(e);
    			}
    		};
    		
    		thenQueue.add(r -> {fullFunc.run();});
    		catchQueue.add(e -> {fullFunc.run();});
    	});
    	
    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncOnFinally(Supplier<Future<R>> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		Runnable fullFunc = () -> {
    			try {
	    			final var funcProm = Promise.fromFuture(func.get());
	    			funcProm.then(r -> {resolve.accept(r);});
	    			funcProm.onCatch(e -> {reject.accept(e);});
    			}
    			catch (Exception e) {
    				reject.accept(e);
    			}
    		};
    		
    		thenQueue.add(r -> {fullFunc.run();});
    		catchQueue.add(r -> {fullFunc.run();});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    // overloads:
    public synchronized Promise<T> then(Consumer<T> func) {
        return then(r -> {
            func.accept(r);
            return result;
        });
    }
    
    public synchronized Promise<T> then(Runnable func) {
        return then(r -> {
            func.run();
            return result;
        });
    }
    
    public synchronized <R> Promise<R> then(Supplier<R> func) {
        return then(r -> {
            return func.get();
        });
    }
    
    public synchronized <R> Promise<R> asyncThen(Supplier<Future<R>> func) {
        return asyncThen(r -> {
            return func.get();
        });
    }
    
    public synchronized Promise<Void> onCatch(Consumer<Exception> func){
    	return onCatch(e -> {
    		func.accept(e);
    		return null;
    	});
    }
    
    public synchronized Promise<Void> onCatch(Runnable func) {
    	return onCatch(e -> {
    		func.run();
    		return null;
    	});
    }
    
    public synchronized <R> Promise<R> asyncOnCatch(Supplier<Future<R>> func) {
        return asyncOnCatch(e -> {
            return func.get();
        });
    }
    
    public synchronized Promise<Void> onFinally(Runnable func){
    	return onFinally(() -> {
    		func.run();
    		return null;
    	});
    }
    // END Then, onError, and onCompletion
    
    // o--------------o
    // | Sub Classes: |
    // o--------------o
    public static class RejectionOfCompletedPromiseException extends RuntimeException{
		private static final long serialVersionUID = 1L;
		private final Promise<?> promise;
    	public Promise<?> getPromise() { return promise; }
    	
    	private RejectionOfCompletedPromiseException(Promise<?> promise) {
    		super("reject was called on a promise which had already been " +
    				(promise.resolved ? "resolved" : "rejected") + ".");
    		this.promise = promise;
    	}
    }
    public static class ResolutionOfCompletedPromiseException extends RuntimeException{
		private static final long serialVersionUID = 1L;
		private final Promise<?> promise;
    	public Promise<?> getPromise() { return promise; }
    	
    	private ResolutionOfCompletedPromiseException(Promise<?> promise) {
    		super("resolve was called on a promise which had already been " +
    				(promise.resolved ? "resolved" : "rejected") + ".");
    		this.promise = promise;
    	}
    }
    // END Sub Classes
    
    // o-------------------o
    // | Static Factories: |
    // o-------------------o
    /**
     * Creates a new promise by running the initializer in a new thread.
     * @param Initializer function that consumes resolve and reject functions. Use them to resolve or reject the promise respectively.
     */
    public static <T> Promise<T> threadInit(BiConsumer<Consumer<T>, Consumer<Exception>> initializer){
    	return new Promise<T>((resolve, reject) -> new Thread(() -> {
    		try {
    			initializer.accept(resolve, reject);
    		}
    		catch (Exception e){
    			reject.accept(e);
    		}
    	}).start());
    }
    
    /**
     * Creates a new promise by running the initializer in a new thread.
     * @param Initializer function that consumes a resolve method. Use it to resolve the promise.
     */
    public static <T> Promise<T> threadInit(Consumer<Consumer<T>> initializer){
    	return new Promise<T>((resolve, reject) -> new Thread(() -> {
    		try {
    			initializer.accept(resolve);
    		}
    		catch (Exception e){
    			reject.accept(e);
    		}
    	}).start());
    }
    
    public static <T> Promise<T> fromFuture(Future<T> future, boolean rejectOnCancel){
    	if (future instanceof Promise<T>) {
    		return (Promise<T>)future;
    	}
    	
    	return Promise.threadInit((resolve, reject) -> {
    		try {
    			resolve.accept(future.get());
    		}
    		catch(ExecutionException ee) {
    			if (ee instanceof Exception) {
    				reject.accept((Exception)ee);
    			}
    			else {
    				reject.accept(ee);
    			}
    		}
    		catch(CancellationException ce) {
    			if (rejectOnCancel) {
    				reject.accept(ce);
    			}
    		}
    		catch(Exception e) {
    			reject.accept(e);
    		}
    	});
    }
    
    public static <T> Promise<T> fromFuture(Future<T> future){
    	return fromFuture(future, true);
    }
    
    // o-----------------------o
    // | Interface Compliance: |
    // o-----------------------o
    /** Only included for interface implementation. Promises cannot be canceled. Will always return false. */
    @Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}
    /** Only included for interface implementation. Promises cannot be canceled. Will always return false. */
	@Override
	public boolean isCancelled() {
		return false;
	}
	/** Added for interface implementation. Equivalent to isComplete. */
	@Override
	public boolean isDone() {
		return isFinalized();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public T get() throws InterruptedException, ExecutionException {
		try {
			return await();
		}
		catch (UncheckedInterruptedException uie) {
			throw uie.getOriginal();
		}
		catch (Exception e){
			throw new ExecutionException(e);
		}
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		try {
			return await(unit.toNanos(timeout) / 1000, (int)(unit.toNanos(timeout) % 1000));
		}
		catch (UncheckedInterruptedException uie) {
			throw uie.getOriginal();
		}
		catch(TimeoutException te) {
			throw te;
		}
		catch(Exception e) {
			throw new ExecutionException(e);
		}
	}
	
	// o--------------------------o
	// | useful static functions: |
	// o--------------------------o
	public static <T> Promise<T> resolved(T value){
		Promise<T> promise = new Promise<T>();
		promise.resolve(value);
		return promise;
	}
	public static <T> Promise<T> rejected(Exception exception){
		Promise<T> promise = new Promise<T>();
		promise.reject(exception);
		return promise;
	}
	public static Promise<Void> all(Iterable<Promise<?>> promises){
		return new Promise<Void>((resolve, reject) -> {
			RefInt count = new RefInt(0);
			RefInt resolvedCount = new RefInt(0);
			RefBoolean countingComplete = new RefBoolean(false);
			RefBoolean isRejected = new RefBoolean(false);
			RefBoolean isResolved = new RefBoolean(false);
			Ref<Exception> rejection = new Ref<>(null);
			
			Object lock = new Object();
			
			for(Promise<?> p : promises) {
				// increment count
				count.set(count.get() + 1);
				
				p.then(() -> {
					// increment resolvedCount
					resolvedCount.set(resolvedCount.get() + 1);
					
					// try for resolve
					if (countingComplete.get()) {
						synchronized(lock) {
							if (!isRejected.get() && !isResolved.get() && resolvedCount.get() == count.get()) {
								isResolved.set(true);
								resolve.accept(null);
							}
						}
					}
				});
				
				p.onCatch(e -> {
					// try for reject
					synchronized(lock) {
						if (rejection.get() == null) {
							rejection.set(e);
							
							if (countingComplete.get()) {
								isRejected.set(true);
								reject.accept(e);
							}
						}
					}
				});
			}
			
			countingComplete.set(true);
			
			synchronized(lock) {
				if (!isResolved.get() && ! isRejected.get()) {
					if (rejection.get() != null) {
						isRejected.set(true);
						reject.accept(rejection.get());
					}
			
					
					if (resolvedCount.get() == count.get()) {
						isResolved.set(true);
						resolve.accept(null);
					}
				}
			}
		});
	}
	
	public static Promise<?> race(Iterable<Promise<?>> promises){
		return new Promise<Object>((resolve, reject) -> {			
			RefBoolean isComplete = new RefBoolean(false);
			Object lock = new Object();
			
			for(Promise<?> p : promises) {
				p.then(r -> {
					synchronized(lock) {
						if (!isComplete.get()) {
							isComplete.set(true);
							resolve.accept(r);
						}
					}
				});
				p.onCatch(e -> {
					synchronized(lock) {
						if (!isComplete.get()) {
							isComplete.set(true);
							reject.accept(e);
						}
					}
				});
			}
		});
	}
	
}