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
public class Async<T> implements Supplier<Promise<T>> {
	private static final long LISTENER_WAIT_MILLISECONDS = 1;
	private static final int LISTENER_WAIT_NANOSECONDS = 0;
	private static AtomicInteger runningInstanceCount = new AtomicInteger(0);
	private static Queue<Async<Object>.CalledInstance> executionQueue = new ConcurrentLinkedQueue<>();
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
		var inst = new CalledInstance();
		return inst.execute();
	}
	
	/**
	 * Runs all async instances in the execution queue.
	 * @throws InterruptedException
	 */
	public static void execute() throws InterruptedException{
		// execution loop
		while(true) {
			Async<Object>.CalledInstance instancePolled;
			while((instancePolled = executionQueue.poll()) != null) {
				final Async<Object>.CalledInstance instance = instancePolled;
				
				// run instance until next yield or completion
				CoThread.Result<Promise<Object>> awaitResult = null;
				Exception exception = null;
				
				try {
					// running instance...
					awaitResult = instance.coThread.await();
				}
				catch (Exception e) {
					// an exception was thrown by the instance.
					exception = e;
				}
				
				// was it an error, yield, or completion?
				if (exception != null) {
					//error:
					instance.reject.accept(exception);
				}
				else if (awaitResult != null) {
					// yield:
					
					// awaitResult contains a promise returned by yield
					// This promise needs to add the instance back onto the execution queue when it completes.
					awaitResult.value.then(() -> {
						executionQueue.add(instance);
					});
					awaitResult.value.error(() -> {
						executionQueue.add(instance);
					});
				}
				else {
					// completion:
					
					// The instance has run to the end of it's function. It has completed execution.
					// it should now contain the result of the execution in it's "result" field.
					instance.resolve.accept(instance.result);
				}
			}
			
			// executionQueue appears to be empty, check if there's still incomplete async.instances
			if (runningInstanceCount.get() > 0) {
				// if so, wait for some time, then start the loop over again.
				Thread.sleep(LISTENER_WAIT_MILLISECONDS, LISTENER_WAIT_NANOSECONDS);
			}
			else {
				// if not, stop the loop. execution is complete.
				break;
			}
		}
	}
	
	
	
	/**
	 * Call to an async function.
	 * @author jesse
	 *
	 */
	private class CalledInstance {
		final CoThread<Promise<Object>> coThread;
		T result = null;
		Promise<T> promise;
		Consumer<T> resolve;
		Consumer<Exception> reject;
		
		CalledInstance() {
			coThread = new CoThread<>(yield -> {
				result = func.apply(new Await(yield));
			}, name);
		}
		
		Promise<T> execute(){
			// start coThread
			coThread.start();
			
			// increments running instance count
			runningInstanceCount.incrementAndGet();
			
			// make a new promise and extract resolve and reject methods
			promise = new Promise<T>((resolve, reject) -> {this.resolve = resolve; this.reject = reject;});
			
			// add callbacks to promise that decrements running instance count when the call completes.
			promise.then(() -> {runningInstanceCount.decrementAndGet();});
			promise.error(() -> {runningInstanceCount.decrementAndGet();});
			
			// get in line
			executionQueue.add((Async<Object>.CalledInstance)this);
			
			// This promise will resolve when to instance completes successfully, and reject when an error occurs
			return promise;
		}
	}
	
	// Await functional class for awaiting promises in an async functional class.
	public static class Await {
		private final Consumer<Promise<Object>> yield;
		
		private Await(Consumer<Promise<Object>> yield) {
			this.yield = yield;
		}
		
		/**
		 * Awaits the given promise, returning it's result when it's resolved.
		 * @param <E> The type of the promise.
		 * @param promise
		 * @return result of promise
		 * @throws AsyncException Wrapper around all Exceptions checked and un-checked. Will contain whatever exception was thrown.
		 * This is the only exception thrown by await.
		 */
		public <E> E apply(Promise<E> promise) throws AsyncException {
			// yield to calling thread
			yield.accept((Promise<Object>)promise);
			
			// at this point yield has stopped blocking which should mean that the promise is complete.
			if (promise.isErrored()) {
				if (promise.getException() instanceof AsyncException) {
					throw (AsyncException)promise.getException();
				}
				else {
					throw new AsyncException(promise.getException());
				}
			}
			else if (promise.isResolved()) {
				return (E)promise.getResult();
			}
			else {
				// if this block runs, something is wrong. Most likely with Async.execute().
				return null;
			}
		}
	}
}
