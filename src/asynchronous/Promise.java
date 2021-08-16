package asynchronous;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import exceptionsPlus.UncheckedWrapper;
/**
 *
 * @author jesse
 */
public class Promise<T> implements Future<T> {
	// queue of functions to run on resolution (like the try block in try/catch)
    private final Queue<Consumer<T>> thenQueue = new LinkedList<>();
    private final Queue<Promise<?>> thenPromises = new LinkedList<>();
    // queue of functions to run on rejection (like the catch block)
    private final Queue<Consumer<Exception>> onErrorQueue = new LinkedList<>();
    // queue of function to run on resolution or rejection (like the finally block)
    private final Queue<Runnable> onCompletionQueue = new LinkedList<>();
    private boolean resolved = false;
    private boolean rejected = false;
    private T result = null;
    private Exception exception = null;
    
    // Whether the promise has been completed (resolved or rejected);
    public boolean isComplete() { return resolved || rejected; }
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
    	onErrorQueue.clear();
    }
    
    private synchronized void handleError() {
    	// run error queue
    	runConsumerQueue(onErrorQueue, exception);
    	
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
    		runRunnableQueue(onCompletionQueue);
    	}
    	else if (resolved) {
    		handleThen();
    		runRunnableQueue(onCompletionQueue);
    	}
    }
    
    synchronized void resolve(T result) throws ResolutionOfCompletedPromiseException{
    	if (resolved || rejected) {
    		throw new ResolutionOfCompletedPromiseException(this);
    	}
    	
        // cancel if this promise has already been resolved.
        if (resolved || rejected) {return;}
        
        // take the result
        this.result = result;
        
        // set resolved
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
        
        // set resolved
        rejected = true;
        
        // run error and complete
        handleCompletionIfComplete();
        
        // notify threads stuck in the await function that the wait is over.
        notifyAll();
    }
    
    public synchronized T await() throws InterruptedException, Exception {
    	notify();
    	while(!resolved && !rejected) {
    		wait();
    	}
    	
    	if (resolved) {
    		return result;
    	}
    	else {
    		throw exception;
    	}
    }
    
    public synchronized T await(long millisecondTimeout, int nanoSecondTimeout) throws InterruptedException, TimeoutException, Exception {
    	AtomicBoolean timedOut = new AtomicBoolean(false);
    	
    	Timing.setTimeout(() ->{
    		timedOut.set(true);
    		notify();
    	}, millisecondTimeout, nanoSecondTimeout);
    	
    	notify();
    	while(!(resolved || rejected || timedOut.get())) {
    		wait();
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
    
    public synchronized T await(long milliseconedTimeout) throws InterruptedException, Exception {
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
    
    public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		thenQueue.add(r -> {
    			try {
	    			final var funcProm = func.apply(r);
	    			funcProm.then(r2 -> {resolve.accept(r2);});
	    			funcProm.onRejection(e -> {reject.accept(e);});
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
    
    public synchronized Promise<T> onRejection(Consumer<Exception> func) {
    	final var prom = new Promise<T>((resolve, reject) -> {
    		onErrorQueue.add(e -> {
    			try {
    				func.accept(e);
    				reject.accept(e);
    			}
    			catch(Exception e2) {
    				reject.accept(e2);
    			}
    		});
    		
    		thenQueue.add(r -> {
    			resolve.accept(r);
    		});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncOnRejection(Function<Exception, Promise<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		onErrorQueue.add(e -> {
    			try {
	    			final var prom2 = func.apply(e);
	    			prom2.then(r -> {resolve.accept(r);});
	    			prom2.onRejection(e2 -> {reject.accept(e2);});
    			}
    			catch(Exception e2) {
    				reject.accept(e2);
    			}
    		});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> onCompletion(Supplier<R> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		onCompletionQueue.add(() -> {
    			try {
	    			final var r = func.get();
	    			resolve.accept(r);
    			}
    			catch(Exception e) {
    				reject.accept(e);
    			}
    		});
    	});
    	
    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncOnCompletion(Supplier<Promise<R>> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		onCompletionQueue.add(() -> {
    			try {
	    			final var funcProm = func.get();
	    			funcProm.then(r2 -> {resolve.accept(r2);});
	    			funcProm.onRejection(e -> {reject.accept(e);});
    			}
    			catch (Exception e) {
    				reject.accept(e);
    			}
    		});
    	});

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized Promise<T> then(Consumer<T> func) {
        return then((r) -> {
            func.accept(r);
            return result;
        });
    }
    
    public synchronized Promise<T> then(Runnable func) {
        return then((r) -> {
            func.run();
            return result;
        });
    }
    
    public synchronized <R> Promise<R> then(Supplier<R> func) {
        return then((r) -> {
            return func.get();
        });
    }
    
    public synchronized <R> Promise<R> asyncThen(Supplier<Promise<R>> func) {
        return asyncThen(r -> {
            return func.get();
        });
    }
    
    public synchronized Promise<T> onRejection(Runnable func) {
    	return onRejection(e -> {
    		func.run();
    	});
    }
    
    public synchronized <R> Promise<R> asyncOnRejection(Supplier<Promise<R>> func) {
        return asyncOnRejection((e) -> {
            return func.get();
        });
    }
    
    public synchronized Promise<Object> onCompletion(Runnable func){
    	return onCompletion(() -> {
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
    
    public static <T> Promise<T> fromFuture(Future<T> future){
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
    		catch(Exception e) {
    			reject.accept(e);
    		}
    	});
    }
    
    // o------------------------o
    // | interface integration: |
    // o------------------------o
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
		return isComplete();
	}
	/** Added for interface implementation. Equivalent to await */
	@Override
	public T get() throws InterruptedException, ExecutionException {
		try {
			return await();
		}
		catch (InterruptedException ie) {
			throw ie;
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
		catch (InterruptedException ie) {
			throw ie;
		}
		catch(TimeoutException te) {
			throw te;
		}
		catch(Exception e) {
			throw new ExecutionException(e);
		}
	}
}