package asynchronous.asyncAwait;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import asynchronous.CoThread;
import asynchronous.Promise;
import exceptionsPlus.UncheckedException;

/**
 * Asyncronouse function used for asyncronouse programming. Call Async.execute at the end of the main method to run called async functions.
 * @author jesse
 *
 * @param <T>
 */
public class Async<T> implements Supplier<Promise<T>>{
	private static final long LISTENER_WAIT_MILLISECONDS = 1;
	private static final int LISTENER_WAIT_NANOSECONDS = 0;
	private static AtomicInteger incompleteInstanceCount = new AtomicInteger(0);
	private static Queue<Async<Object>.Instance> executionQueue = new ConcurrentLinkedQueue<>();
	private Function<Await, T> func;
	private String name = null;
	
	public String getName() { return name; }
	
	public Async(Function<Await, T> func) {
		this.func = func;
	}
	public Async(Function<Await, T> func, String name) {
		this.func = func;
		this.name = name;
	}
	
	public Promise<T> get(){
		var inst = new Instance(func, this);
		return inst.execute();
	}
	
	/**
	 * Runs all async instances in the execution queue.
	 * @throws InterruptedException
	 */
	public static void execute() throws InterruptedException{
		// execution loop
		while(true) {
			Async<Object>.Instance instancePolled;
			while((instancePolled = executionQueue.poll()) != null) {
				final Async<Object>.Instance instance = instancePolled;
				
				// run instance until next yield or completion
				CoThread.Result<Promise<Object>> awaitResult = null;
				Exception exception = null;
				
				try {
					awaitResult = instance.coThread.await();
				}
				catch (Exception e) {
					exception = e;
				}
				
				// was it an error, yield, or completion?
				if (exception != null) {
					//error:
					instance.reject.accept(exception);
				}
				else if (awaitResult != null) {
					// yield:
					
					// add logic to promise
					// put instance back on queue
					awaitResult.value.then(() -> {
						executionQueue.add(instance);
					});
					awaitResult.value.error(() -> {
						executionQueue.add(instance);
					});
				}
				else {
					// completion:
					instance.resolve.accept(instance.result);
				}
			}
			
			// executionQueue appears to be empty, check if there's still incomplete async.instances
			if (incompleteInstanceCount.get() > 0) {
				// if so, wait for some time, then start the loop over again.
				Thread.sleep(LISTENER_WAIT_MILLISECONDS, LISTENER_WAIT_NANOSECONDS);
			}
			else {
				// if not, stop the loop. execution is complete.
				break;
			}
		}
	}
	
	
	// the following code is still jank
	
	/**
	 * Call to an async function.
	 * @author jesse
	 *
	 */
	class Instance{
		CoThread<Promise<Object>> coThread;
		T result;
		Promise<T> promise;
		Consumer<T> resolve;
		Consumer<Exception> reject;
		Async<T> parent;
		
		public boolean notComplete() { return coThread.notComplete(); }
		public boolean isComplete() { return coThread.isComplete(); }
		
		Instance(Function<Await, T> func, Async<T> parent){
			this.parent = parent;
			
			coThread = new CoThread<>(yield -> {
				result = func.apply(new Await(yield, (Async<Object>.Instance)this));
			}, parent.getName());
		}
		
		public Promise<T> execute(){
			coThread.start();
			incompleteInstanceCount.incrementAndGet();
			promise = new Promise<T>((resolve, reject) -> {this.resolve = resolve; this.reject = reject;});
			promise.then(() -> {incompleteInstanceCount.decrementAndGet();});
			promise.error(() -> {incompleteInstanceCount.decrementAndGet();});
			executionQueue.add((Async<Object>.Instance)this);
			return promise;
		}
	}
	
	// Await functional class for awaiting promises in an async functional class.
	public static class Await{
		private Consumer<Promise<Object>> yield;
		private Object result;
		private Exception exception;
		private Async<Object>.Instance instance;
		
		Await(Consumer<Promise<Object>> yield, Async<Object>.Instance isntance) {
			this.instance = isntance;
			this.yield = yield;
		}
		
		// Awaits the given promise, returning it's result when it's resolved.
		public <E> E apply(Promise<E> promise) throws UncheckedException, RuntimeException {
			promise.then(r -> {result = r;});
			promise.error(e -> {exception = e;});
			yield.accept((Promise<Object>)promise);
			
			if (promise.isErrored()) {
				if (AsyncException.class.isAssignableFrom(exception.getClass())) {
					throw (AsyncException)exception;
				}
				else {
					throw new AsyncException(exception);
				}
			}
			else if (promise.isResolved()) {
				return (E)result;
			}
			else {
				return null;
			}
		}
	}
}
