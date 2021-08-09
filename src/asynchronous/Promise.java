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
	// queue of functions to run on resolution (like the try block in try/catch)
    private final Queue<Consumer<T>> thenQueue = new LinkedList<>();
    private final Queue<Promise<?>> thenPromises = new LinkedList<>();
    // queue of functions to run on rejection (like the catch block)
    private final Queue<Consumer<Exception>> errorQueue = new LinkedList<>();
    // queue of function to run on resolution or rejection (like the finally block)
    private final Queue<Runnable> completeQueue = new LinkedList<>();
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
    }
    
    private synchronized void handleError() {
    	// run error queue
    	runConsumerQueue(errorQueue, exception);
    	
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
    		runRunnableQueue(completeQueue);
    	}
    	else if (resolved) {
    		handleThen();
    		runRunnableQueue(completeQueue);
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

    // o----------------------------o
    // | Then, Error, and Complete: |
    // o----------------------------o
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
	    			funcProm.error(e -> {reject.accept(e);});
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

    	handleCompletionIfComplete();
    	
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

    	handleCompletionIfComplete();
    	
    	return prom;
    }
    
    public synchronized <R> Promise<R> complete(Supplier<R> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		completeQueue.add(() -> {
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
    
    public synchronized <R> Promise<R> asyncComplete(Supplier<Promise<R>> func){
    	final var prom = new Promise<R>((resolve, reject) -> {
    		completeQueue.add(() -> {
    			try {
	    			final var funcProm = func.get();
	    			funcProm.then(r2 -> {resolve.accept(r2);});
	    			funcProm.error(e -> {reject.accept(e);});
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
    
    public synchronized Promise<Object> complete(Runnable func){
    	return complete(() -> {
    		func.run();
    		return null;
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