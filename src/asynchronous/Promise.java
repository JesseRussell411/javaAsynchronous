package asynchronous;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.*;

import exceptionsPlus.UncheckedException;
/**
 *
 * @author jesse
 */
public class Promise<T> {
    private final Queue<Consumer<T>> thenQueue = new LinkedList<>();
    private final Queue<Promise<?>> thenPromises = new LinkedList<>();
    private final Queue<Consumer<Exception>> errorQueue = new LinkedList<>();
    private boolean resolved = false;
    private boolean rejected = false;
    private T result = null;
    private Exception exception = null;
    
    public boolean isResolved() { return resolved; }
    public boolean isRejected() { return rejected; }
    public boolean isComplete() { return resolved || rejected; }
    public T getResult() { return result; }
    public Exception getException() { return exception; }
    
    Promise(){}
    
    public Promise(BiConsumer<Consumer<T>, Consumer<Exception>> initializer) {
        initializer.accept((r) -> resolve(r), (e) -> reject(e));
    }

    public Promise(Consumer<Consumer<T>> initializer){
        initializer.accept((t) -> resolve(t));
    }
    
    private synchronized void runThenQueue(){
    	Consumer<T> then;
    	while((then = thenQueue.poll()) != null) {
    		then.accept(result);
    	}
    }
    
    private synchronized void runErrorQueue() {
    	Consumer<Exception> error;
    	while((error = errorQueue.poll()) != null) {
    		error.accept(exception);
    	}
    	
    	// error out all then's
    	Promise<?> prom;
    	while((prom = thenPromises.poll()) != null) {
    		prom.reject(exception);
    	}
    	
    	thenQueue.clear();
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
        
        // run the next function (given to us by the then method)
        runThenQueue();
        
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
        
        // run the next function (given to us by the then method)
        runErrorQueue();
        
        // notify threads stuck in the await function that the wait is over.
        notifyAll();
    }
    
    public synchronized T await() throws InterruptedException {
    	while(!resolved && !rejected) {
    		wait();
    	}
    	
    	if (resolved) {
    		return result;
    	}
    	else {
    		throw new UncheckedException(exception);
    	}
    }

    // o-----------------o
    // | Then and Error: |
    // o-----------------o
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
    	if (rejected) {
    		runErrorQueue();
    	}
    	if (resolved) {
    		runThenQueue();
    	}
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		thenQueue.add(r -> {
    			try {
	    			final var funcProm = func.apply(r);
	    			funcProm.then(r2 -> {resolve.accept(r2);});
	    			funcProm.error(e -> {reject.accept(e);});
    			}
    			catch (Exception e) {
    				reject.accept(e);
    			}
    		});
    	});
    	thenPromises.add(prom);
    	if (rejected) {
    		runErrorQueue();
    	}
    	if (resolved) {
    		runThenQueue();
    	}
    	return prom;
    }
    
    public synchronized Promise<T> error(Consumer<Exception> func) {
    	final var prom = new Promise<T>((resolve, reject) -> {
    		errorQueue.add(e -> {
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
    	if (rejected) {
    		runErrorQueue();
    	}
    	if (resolved) {
    		runThenQueue();
    	}
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncError(Function<Exception, Promise<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		errorQueue.add(e -> {
    			try {
	    			final var prom2 = func.apply(e);
	    			prom2.then(r -> {resolve.accept(r);});
	    			prom2.error(e2 -> {reject.accept(e2);});
    			}
    			catch(Exception e2) {
    				reject.accept(e2);
    			}
    		});
    	});
    	if (rejected) {
    		runErrorQueue();
    	}
    	if (resolved) {
    		runThenQueue();
    	}
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
    
    public synchronized Promise<T> error(Runnable func) {
    	return error(e -> {
    		func.run();
    	});
    }
    
    public synchronized <R> Promise<R> asyncError(Supplier<Promise<R>> func) {
        return asyncError((e) -> {
            return func.get();
        });
    }
    // END Then and Error
    
    // o--------------o
    // | Sub Classes: |
    // o--------------o
    public static class RejectionOfCompletedPromiseException extends RuntimeException{
		private static final long serialVersionUID = 1L;
		private final Promise<?> promise;
    	public Promise<?> getPromise() { return promise; }
    	
    	private RejectionOfCompletedPromiseException(Promise<?> promise) {
    		super("reject was called on a promise which had already been " +
    				(promise.resolved ? "resolve" : "rejected") + ".");
    		this.promise = promise;
    	}
    }
    public static class ResolutionOfCompletedPromiseException extends RuntimeException{
		private static final long serialVersionUID = 1L;
		private final Promise<?> promise;
    	public Promise<?> getPromise() { return promise; }
    	
    	private ResolutionOfCompletedPromiseException(Promise<?> promise) {
    		super("resolve was called on a promise which had already been " +
    				(promise.resolved ? "resolve" : "rejected") + ".");
    		this.promise = promise;
    	}
    }
    // END Sub Classes
}