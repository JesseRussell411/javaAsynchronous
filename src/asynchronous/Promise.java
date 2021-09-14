package asynchronous;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
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
    private final Queue<Consumer<Throwable>> catchQueue = new LinkedList<>();
    private boolean resolved = false;
    private boolean rejected = false;
    private T result = null;
    private Throwable exception = null;
    
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
    public Throwable getException() { return exception; }
    
    Promise(){}
    
    /**
     * Runs the initializer to create a promise. NOTE: The initializer will block the calling function as the initializer is called in the same thread.
     * To initialize asynchronously use "Promise.threadInit" instead or start a new thread inside the initializer.
     * @param Initializer function that consumes resolve and reject functions. Use them to resolve or reject the promise respectively.
     */
    public Promise(BiConsumer<Consumer<T>, Consumer<Throwable>> initializer) {
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
    
    synchronized void reject(Throwable exception) throws RejectionOfCompletedPromiseException{
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
    
    public synchronized T await() throws UncheckedInterruptedException, Throwable {
    	notify();
    	try {
	    	while(!isFinalized()) {
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
    
    public synchronized T await(long millisecondTimeout, int nanoSecondTimeout) throws UncheckedInterruptedException, TimeoutException, Throwable {
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
    
    public synchronized T await(long milliseconedTimeout) throws UncheckedInterruptedException, Throwable {
    	return await(milliseconedTimeout, 0);
    }

    // o----------------------------------o
    // | Then, onError, and onCompletion: |
    // o----------------------------------o
    // then:
    public synchronized <R> Promise<R> thenApply(Function<T, R> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		thenQueue.add(r -> {
    			try {
	    			final var r2 = func.apply(r);
	    			resolve.accept(r2);
    			}
    			catch(Throwable e) {
    				reject.accept(e);
    			}
    		});
    	});
    	thenPromises.add(prom);
    	
    	handleCompletionIfComplete();
    	
    	return prom;
    }
    public synchronized <R> Promise<R> then(Function<T, R> func){
    	return thenApply(func);
    }
    
    public synchronized Promise<T> thenAccept(Consumer<T> func) {
        return thenApply(r -> {
            func.accept(r);
            return result;
        });
    }
    public synchronized Promise<T> then(Consumer<T> func) {
    	return thenAccept(func);
    }
    
    public synchronized Promise<T> thenRun(Runnable func) {
        return thenApply(r -> {
            func.run();
            return result;
        });
    }
    public synchronized Promise<T> then(Runnable func){
    	return thenRun(func);
    }
    
    public synchronized <R> Promise<R> thenGet(Supplier<R> func) {
        return thenApply(r -> {
            return func.get();
        });
    }
    public synchronized <R> Promise<R> then(Supplier<R> func) {
    	return thenGet(func);
    }
    
    // asyncThen -- like then but the callback must return a Future
    public synchronized <R> Promise<R> asyncThenApply(Function<T, Future<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		thenQueue.add(r -> {
    			try {
	    			final var funcProm = Promise.fromFuture(func.apply(r));
	    			funcProm.then(r2 -> {resolve.accept(r2);});
	    			funcProm.onCatch(e -> {reject.accept(e);});
    			}
    			catch (Throwable e) {
    				reject.accept(e);
    			}
    		});
    	});
    	thenPromises.add(prom);

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    public synchronized <R> Promise<R> asyncThen(Function<T, Future<R>> func) {
    	return asyncThenApply(func);
    }
    
    public synchronized <R> Promise<R> asyncThenGet(Supplier<Future<R>> func) {
    	return asyncThenApply(r -> {
    		return func.get();
    	});
    }
    
    public synchronized <R> Promise<R> asyncThen(Supplier<Future<R>> func) {
    	return asyncThenGet(func);
    }
    
    // onCatch -- because I couldn't call it catch
    public synchronized <R> Promise<R> onCatchApply(Function<Throwable, R> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		catchQueue.add(e -> {
    			try {
    				resolve.accept(func.apply(e));
    			}
    			catch(Throwable e2) {
    				reject.accept(e2);
    			}
    		});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    public synchronized <R> Promise<R> onCatch(Function<Throwable, R> func) {
    	return onCatchApply(func);
    }
    
    public synchronized Promise<Void> onCatchAccept(Consumer<Throwable> func){
    	return onCatchApply(e -> {
    		func.accept(e);
    		return null;
    	});
    }
    
    public synchronized Promise<Void> onCatch(Consumer<Throwable> func){
    	return onCatchAccept(func);
    }
    
    public synchronized Promise<Void> onCatchRun(Runnable func) {
    	return onCatchApply(e -> {
    		func.run();
    		return null;
    	});
    }
    
    public synchronized Promise<Void> onCatch(Runnable func) {
    	return onCatchRun(func);
    }
    
    public synchronized <R> Promise<R> onCatchGet(Supplier<R> func){
    	return onCatchApply(e -> func.get());
    }
    
    public synchronized <R> Promise<R> onCatch(Supplier<R> func){
    	return onCatchGet(func);
    }
    
    public synchronized <R> Promise<R> asyncOnCatchApply(Function<Throwable, Future<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		catchQueue.add(e -> {
    			try {
	    			final var funcProm = Promise.fromFuture(func.apply(e));
	    			funcProm.then(r -> {resolve.accept(r);});
	    			funcProm.onCatch(e2 -> {reject.accept(e2);});
    			}
    			catch(Throwable e2) {
    				reject.accept(e2);
    			}
    		});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncOnCatch(Function<Throwable, Future<R>> func){
    	return asyncOnCatchApply(func);
    }
    
    public synchronized <R> Promise<R> asyncOnCatchGet(Supplier<Future<R>> func) {
    	return asyncOnCatchApply(e -> {
    		return func.get();
    	});
    }
    public synchronized <R> Promise<R> asyncOnCatch(Supplier<Future<R>> func) {
    	return asyncOnCatchGet(func);
    }
    
    // onFinally -- because I couldn't call it finally
    public synchronized <R> Promise<R> onFinallyGet(Supplier<R> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		Runnable fullFunc = () -> {
    			try {
	    			resolve.accept(func.get());
    			}
    			catch(Throwable e) {
    				reject.accept(e);
    			}
    		};
    		
    		thenQueue.add(r -> {fullFunc.run();});
    		catchQueue.add(e -> {fullFunc.run();});
    	});
    	
    	handleCompletionIfComplete();
    	
    	return prom;
    }
    public synchronized <R> Promise<R> onFinally(Supplier<R> func){
    	return onFinallyGet(func);
    }
    
    public synchronized Promise<Void> onFinallyRun(Runnable func){
    	return onFinallyGet(() -> {
    		func.run();
    		return null;
    	});
    }
    public synchronized Promise<Void> onFinally(Runnable func){
    	return onFinallyRun(func);
    }
    
    public synchronized <R> Promise<R> asyncOnFinallyGet(Supplier<Future<R>> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		Runnable fullFunc = () -> {
    			try {
	    			final var funcProm = Promise.fromFuture(func.get());
	    			funcProm.then(r -> {resolve.accept(r);});
	    			funcProm.onCatch(e -> {reject.accept(e);});
    			}
    			catch (Throwable e) {
    				reject.accept(e);
    			}
    		};
    		
    		thenQueue.add(r -> {fullFunc.run();});
    		catchQueue.add(r -> {fullFunc.run();});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    public synchronized <R> Promise<R> asyncOnFinally(Supplier<Future<R>> func) {
    	return asyncOnFinallyGet(func);
    }
    // END Then, onCatch, and onFinally
    
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
    public static <T> Promise<T> threadInit(BiConsumer<Consumer<T>, Consumer<Throwable>> initializer){
    	return new Promise<T>((resolve, reject) -> new Thread(() -> {
    		try {
    			initializer.accept(resolve, reject);
    		}
    		catch (Throwable e){
    			reject.accept(e);
    		}
    	}).start());
    }
    
    /**
     * Creates a new thread which runs the possibly blocking initializer
     * function and returns a promise that resolves when that function
     * completes or rejects when in encounters an error.
     * @return Whatever was returned by the initializer
     */
    public static <T> Promise<T> threadInit(Supplier<T> initializer){
    	return new Promise<T>((resolve, reject) -> new Thread(() -> {
    		try {
    			resolve.accept(initializer.get());
    		}
    		catch (Throwable e) {
    			reject.accept(e);
    		}
    	}));
    }
    
    /**
     * Creates a new thread which runs the possibly blocking initializer
     * function and returns a promise that resolves when that function
     * completes or rejects when in encounters an error.
     */
    public static Promise<Void> threadInit(Runnable initializer){
    	return Promise.<Void>threadInit(() -> {
    		initializer.run();
    		return null;
    	});
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
    		catch (Throwable e){
    			reject.accept(e);
    		}
    	}).start());
    }
    
    public static <T> Promise<T> fromFuture(Future<T> future){
    	// special cases:
    	if (future instanceof CompletableFuture<T> cf) {
    		return new Promise<T>((resolve, reject) -> {
    			cf.thenAccept(r -> resolve.accept(r));
    			cf.exceptionally(e -> {
    				reject.accept(e);
    				return null;
    			});
    		});
    	}
    	else if (future instanceof Promise<T> p) {
    		return p;
    	}
    	else if (future instanceof Task<T> t) {
    		return t.getPromise();
    	}
    	else if (future instanceof Deferred<T> d) {
    		return d.getPromise();
    	}
    	
    	// unspecial cases:
    	return Promise.threadInit((resolve, reject) -> {
    		try {
    			resolve.accept(future.get());
    		}
    		catch(ExecutionException ee) {
    			reject.accept(ee.getCause());
    		}
    		catch(CancellationException ce) {
				reject.accept(ce);
    		}
    		catch(Throwable e) {
    			reject.accept(e);
    		}
    	});
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
		catch (Throwable e){
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
		catch(Throwable e) {
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
	public static <T> Promise<T> rejected(Throwable exception){
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
			Ref<Throwable> rejection = new Ref<>(null);
			
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