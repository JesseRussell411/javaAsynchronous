package asynchronous;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.*;
/**
 *
 * @author jesse
 */
public class Promise<T> {
    private Queue<Consumer<T>> thenQueue = new LinkedList<>();
    private boolean resolved = false;
    private T result;
    
    public boolean isResolved() { return resolved; }
    public T getResult() { return result; }
    
    Promise(){}
    
    public Promise(Consumer<Consumer<T>> func){
        func.accept((t) -> resolve(t));
    }
    
    private void runThenQueue(){
    	Consumer<T> then;
    	while((then = thenQueue.poll()) != null) {
    		then.accept(result);
    	}
    }
    
    public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> func){
    	final var prom = new Promise<R>(resolve -> {
    		thenQueue.add(r -> {
    			func.apply(r).then(r2 -> {resolve.accept(r2);});
    		});
    	});
    	if (resolved) {
    		runThenQueue();
    	}
    	return prom;
    }
    
    public synchronized <R> Promise<R> then(Function<T, R> func){
    	final var prom = new Promise<R>(resolve -> {
	    	thenQueue.add(r -> {
	    		resolve.accept(func.apply(r));
	    	});
    	});
    	if (resolved) {
    		runThenQueue();
    	}
    	return prom;
    }
    
    public synchronized Promise<T> then(Consumer<T> func){
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
    
    synchronized void resolve(T result){
        // cancel if this promise has already been resolved.
        if (resolved) {return;}
        
        // take the result
        this.result = result;
        
        // set resolved
        resolved = true;
        
        // run the next function (given to us by the then method)
        runThenQueue();
        
        // notify threads stuck in the await function that the wait is over.
        notifyAll();
    }
    
    public synchronized T await() throws InterruptedException{
    	while(!resolved) {
    		wait();
    	}
    	return result;
    }
}