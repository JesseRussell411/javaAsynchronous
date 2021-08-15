package asynchronous.asyncAwait;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import asynchronous.CoThread;
import asynchronous.Deferred;
import asynchronous.Promise;
import asynchronous.Timing;
import asynchronous.UncheckedInterruptedException;
import exceptionsPlus.UncheckedWrapper;

/**
 * Asynchronous function used for asynchronous programming. Call Async.execute at the end of the main method to run called Async functions.
 * @author jesse
 *
 * @param <T>
 */
public class Async<T> implements Supplier<Promise<T>> {
	private static final AtomicInteger runningInstanceCount = new AtomicInteger(0);
	private static final Queue<Async<?>.CalledInstance> executionQueue = new ConcurrentLinkedQueue<>();
	private final Function<Await, T> func;
	private final String name;
	private static final Object executeWaitLock = new Object();
	
	/**
	 * Notify Async class that the instance has started
	 */
	private static void asyncStartNotify(Async<?>.CalledInstance inst) {
		synchronized(executeWaitLock) {
			executionQueue.add(inst);
			runningInstanceCount.incrementAndGet();
			executeWaitLock.notify();
		}
	}
	
	/**
	 * Notify Async class that an awaited promise has completed
	 * @param inst The instance awaiting the promise
	 */
	private static void asyncAwaitCompleteNofify(Async<?>.CalledInstance inst) {
		synchronized(executeWaitLock) {
			executionQueue.add(inst);
			executeWaitLock.notify();
		}
	}
	
	/**
	 * Notify Async class that an instance has completed
	 */
	private static void asyncCompleteNotify() {
		synchronized(executeWaitLock) {
			runningInstanceCount.decrementAndGet();
			executeWaitLock.notify();
		}
	}
	
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
		return inst.start();
	}
	
	/**
	 * Runs all Async instances in the execution queue.
	 * @throws InterruptedException
	 */
	public static void execute() throws InterruptedException{
		// execution loop
		do {
			Async<?>.CalledInstance instance;
			while((instance = executionQueue.poll()) != null) {
				instance.execute();
			}
			
			// execution queue is empty, as long as there's still instances running:
			// Wait for the execution queue to be enqueued with something to run
			// or for there to be no running instances.
			synchronized(executeWaitLock) {
				while(runningInstanceCount.get() > 0 && executionQueue.isEmpty()) {
					executeWaitLock.wait();
				}
			}
			
			// wait over, if there are no running instances and the executionQueue is empty: 
			// break and finish execution.
		} while(!(runningInstanceCount.get() == 0 && executionQueue.isEmpty()));
	}
	
	
	
	/**
	 * Call to an Async function.
	 * @author jesse
	 *
	 */
	private class CalledInstance {
		private final CoThread<Promise<?>> coThread;
		private T result = null;
		private Deferred<T> deferred;
		
		private void resolve(T result) {
			deferred.resolve(result);
		}
		private void reject(Exception exception) {
			deferred.reject(exception);
		}
		public T getResult() { return result; }
		public void execute() throws InterruptedException {
			boolean coThreadYielded = false;
			Exception exception = null;
			try {
				coThreadYielded = coThread.await();
			}
			catch(UncheckedInterruptedException ie) {
				throw ie.getOriginal();
			}
			catch(Exception e) {
				exception = e;
			}
			
			// was it an error, yield, or completion?
			if (exception != null) {
				//error:
				reject(exception);
			}
			else if (coThreadYielded) {
				// yield:
				if (coThread.getResult() == null) {
					throw new NullPointerException("Promise given to await.accept was null.");
				}
				
				// awaitResult contains a promise returned by yield
				// This promise needs to add the instance back onto the execution queue when it completes.
				coThread.getResult().onCompletion(() -> {
					asyncAwaitCompleteNofify(this);
				});
			}
			else {
				// completion:
				
				// The instance has run to the end of it's function. It has completed execution.
				// it should now contain the result of the execution in it's "result" field.
				resolve(result);
			}
		}
		
		CalledInstance() {
			coThread = new CoThread<>(yield -> {
				result = func.apply(new Await(yield));
			}, name);
		}
		
		Promise<T> start(){
			// start coThread
			coThread.start();
			
			// make a new promise and extract resolve and reject methods
			deferred = new Deferred<T>();
			
			// add callback to promise that decrements running instance count when the call completes.
			deferred.complete(() -> {
				asyncCompleteNotify();
			});
			
			// Notify Async class that this instance has started.
			asyncStartNotify(this);
			
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
		 * @throws UncheckedWrapper Wrapper around all Exceptions checked and un-checked. Will contain whatever exception was thrown.
		 * This is the only exception thrown by await.
		 */
		public <E> E apply(Promise<E> promise) throws UncheckedWrapper {
			// yield to Async.execute. wait for the promise to complete. Async.execute will take care of that.
			yield.accept((Promise<?>)promise);
			
			// at this point yield has stopped blocking which should mean that the promise is complete.
			if (promise.isRejected()) {
				if (promise.getException() instanceof UncheckedWrapper) {
					throw (UncheckedWrapper)promise.getException();
				}
				else {
					throw new UncheckedWrapper(promise.getException());
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
		
		// utils:
		/**
		 * Non-blocking sleep function. May sleep for longer than the specified time while it waits it's turn to execute.
		 */
		public void sleep(long milliseconds, int nanoseconds) {
			apply(Timing.setTimeout(() -> null, milliseconds, nanoseconds));
		}
		
		/**
		 * Non-blocking sleep function. May sleep for longer than the specified time while it waits it's turn to execute.
		 */
		public void sleep(long milliseconds) {
			apply(Timing.setTimeout(() -> null, milliseconds));
		}
	}
}
