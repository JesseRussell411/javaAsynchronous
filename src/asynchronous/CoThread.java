package asynchronous;

import asynchronous.futures.*;
import java.util.function.*;
import functionPlus.*;

/**
 * A thread that acts like a coroutine. Like a normal Thread, this class's
 * constructor takes a functional class to run concurrently except this class
 * supplies that functional class with an instance of a class called Yield.
 * Yield has a method called accept which takes a value of type T and then
 * blocks until the CoThread is signaled to run again by its "run" method. The
 * run method returns a promise which is settled by the CoThread yielding,
 * dying (completing execution), or throwing an error. 
 * @author Jesse Russell
 *
 * @param <R> The type of the value yielded with.
 */
public class CoThread<R> implements AutoCloseable{
	private final Thread thread;
	private final Yield yield = new Yield();
	private volatile Deferred<Result<R>> deferred;
	
	// flags:
	private volatile boolean running = false;
	private volatile boolean started = false;
	private volatile boolean dead = false;
	
	// flag getters:
	/** Whether the run method has been called and the CoThread has not yet yielded or died. */
	public boolean isRunning() { return running; }
	/** Whether the run method has been called at least once. */
	public boolean isStarted() { return started; }
	/** Whether the CoThread has ran to the end of its body and died. */
	public boolean isDead() { return dead; }
	
	public CoThread(Consumer<Yield> func, String name) {
		thread = new Thread(makeBody(func), name);
	}
	public CoThread(Consumer<Yield> func) {
		thread = new Thread(makeBody(func));
	}
	
	private Runnable makeBody(Consumer<Yield> func) {
		return () -> {
			synchronized(yield) {
				try {
					func.accept(yield);
				}
				catch(Throwable e) {
					deferred.reject(e);
				}
				finally {
					running = false;
					dead = true;
					deferred.resolve(new Result<R>());
				}
			}
		};
	}
	
	/**
	 * Runs the CoThread.
	 * @return Resolves when the CoThread either yields or dies. If it yields:
	 * the result will hold the value yielded with. If it dies: the result will
	 * be undefined.
	 */
	public synchronized Promise<Result<R>> run() {
		if (running || dead)
			return deferred.promise;
		
		synchronized(yield) {
			if (running || dead) {
				return deferred.promise;
			}
			else {
				deferred = new Deferred<Result<R>>();
				running = true;
				
				if (!started) {
					thread.start();
					started = true;
				}
				
				yield.notifyAll();
				
				return deferred.promise;
			}
		}
	}
	
	/** Functional class for yielding with a value. Blocks until the next call to run. */
	public class Yield {
		private Yield() {}
		
		/**
		 * Yield with the given value. Blocks until the next call to run.
		 * @throws InterruptedException As usual, be sure to handle this
		 * exception appropriately and halt execution. This is how the CoThread
		 * is closed.
		 */
		public synchronized void accept(R value) throws InterruptedException {
			running = false;
			deferred.resolve(new Result<R>(value));
			
			while(!running) {
				wait();
			}
		}
	}

	@Override
	public void close() {
		thread.interrupt();
	}
}
