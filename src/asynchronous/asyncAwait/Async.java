package asynchronous.asyncAwait;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import asynchronous.CoThread;
import asynchronous.Deferred;
import asynchronous.Promise;

/**
 * Asyncronouse function used for asyncronouse programming. Call Async.execute at the end of the main method to run called async functions.
 * @author jesse
 *
 * @param <T>
 */
public class Async<T> implements Supplier<Promise<T>> {
	private static final long LISTENER_WAIT_MILLISECONDS = 1;
	private static final int LISTENER_WAIT_NANOSECONDS = 0;
	private static final AtomicInteger runningInstanceCount = new AtomicInteger(0);
	private static final Queue<Async<?>.CalledInstance> executionQueue = new ConcurrentLinkedQueue<>();
	private final Function<Await, T> func;
	private final String name;
	
	public String getName() { return name; }
	
	public Async(Function<Await, T> func) {
		this.func = func;
		this.name = null;
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
			Async<?>.CalledInstance instancePolled;
			while((instancePolled = executionQueue.poll()) != null) {
				final Async<?>.CalledInstance instance = instancePolled;
				
				// run instance until next yield or completion
				CoThread.Result<Promise<?>> awaitResult = null;
				Exception exception = null;
				
				try {
					// running instance...
					awaitResult = instance.await();
				}
				catch (Exception e) {
					// an exception was thrown by the instance.
					exception = e;
				}
				
				// was it an error, yield, or completion?
				if (exception != null) {
					//error:
					instance.reject(exception);
				}
				else if (awaitResult != null) {
					// yield:
					
					// awaitResult contains a promise returned by yield
					// This promise needs to add the instance back onto the execution queue when it completes.
					awaitResult.value.complete(() -> {
						executionQueue.add(instance);
					});
				}
				else {
					// completion:
					
					// The instance has run to the end of it's function. It has completed execution.
					// it should now contain the result of the execution in it's "result" field.
					instance.resolveWithResult();
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
		private final CoThread<Promise<?>> coThread;
		private T result = null;
		private Deferred<T> deferred;
		public void resolve(T result) {
			deferred.resolve(result);
		}
		public void reject(Exception exception) {
			deferred.reject(exception);
		}
		public void resolveWithResult() {
			resolve(result);
		}
		public T getResult() { return result; }
		public CoThread.Result<Promise<?>> await() throws InterruptedException { return coThread.await(); }
		
		
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
			deferred = new Deferred<T>();
			
			// add callback to promise that decrements running instance count when the call completes.
			deferred.complete(() -> {runningInstanceCount.decrementAndGet();});
			
			// get in line
			executionQueue.add(this);
			
			// This promise will resolve when the instance completes successfully, and reject when an error occurs
			return deferred.getPromise();
		}
	}
	
	// Await functional class for awaiting promises in an Async functional class.
	public static class Await {
		private final Consumer<Promise<?>> yield;
		
		// can't be instantiated by the user. Only Async and itself (but only Async should)
		private Await(Consumer<Promise<?>> yield) {
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
			// yield to Async.execute. wait for the promise to complete. Async.execute will take care of that.
			yield.accept((Promise<?>)promise);
			
			// at this point yield has stopped blocking which should mean that the promise is complete.
			if (promise.isRejected()) {
				if (promise.getException() instanceof AsyncException) {
					throw (AsyncException)promise.getException();
				}
				else {
					throw new AsyncException(promise.getException());
				}
			}
			else if (promise.isResolved()) {
				return promise.getResult();
			}
			else {
				// if this block runs, something is wrong. Most likely with Async.execute().
				System.err.println("There is something wrong with Async.execute (most likely). After yielding in Await.apply, the promise is still not complete.");
				return null;
			}
		}
	}
}
