package asynchronous;

import asynchronous.futures.*;
import java.util.function.*;
import functionPlus.*;

public class CoThread<R> implements AutoCloseable{
	private final Thread thread;
	private volatile Deferred<Result<R>> deferred;
	private final Yield yield = new Yield();
	
	// flags:
	private volatile boolean running = false;
	private volatile boolean started = false;
	private volatile boolean dead = false;
	
	// flag getters:
	public boolean isRunning() { return running; }
	public boolean isStarted() { return started; }
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
	
	public synchronized Promise<Result<R>> run() {
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
	
	public class Yield {
		private Yield() {}
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





//
//
//
//
//
//public class CoThread<R> implements AutoCloseable{
//	private final ThreadHolder<R> that;
//	public CoThread(Consumer<Consumer<R>> func) {
//		that = new ThreadHolder(func);
//	}
//	
//	
//	
//	
//	public class Yield {
//		private Yield() {}
//		public void accept(R result) throws InterruptedException {
//			synchronized(that) {
//				that.result = result;
//				that.running = false;
//				that.deferred.resolve(true);
//				
//				
//				that.notifyAll();
//				while(!that.running) {
//					that.wait();
//				}
//			}
//		}
//	}
//	
//	private class ThreadHolder<R>{
//		final Thread thread;
//		R result = null;
//		boolean completed = false;
//		boolean running = false;
//		boolean started = false;
//		Deferred<Result<R>> deferred = null;
//		ThreadHolder(Consumer<Yield> func){
//			thread = new Thread(() -> {
//				started = true;
//				func.accept(new Yield());
//				
//				//complete
//				completed = true;
//				running = false;
//			});
//		}
//	}
//}



//public class CoThread<T> implements AutoCloseable {
//	private CoThreadHolder<T> threadHolder;
//	public boolean notComplete() { return threadHolder.notComplete(); }
//	public boolean isComplete() { return threadHolder.isComplete(); }
//	public boolean isErrored() { return threadHolder.isErrored(); }
//	public boolean started() { return threadHolder.started(); }
//	public String getName() { return threadHolder.getName(); }
//	public T getResult() { return threadHolder.getResult(); }
//	
//	public CoThread(Consumer<Consumer<T>> routine, String name) { threadHolder = new CoThreadHolder<>(routine, name); }
//	public CoThread(Consumer<Consumer<T>> routine) { threadHolder = new CoThreadHolder<>(routine); }
//	
//	public CoThread<T> start() { 
//		threadHolder.start();
//		return this;
//	}
//	
//	/** 
//	 * run CoThread. Blocking until yield. 
//	 * @return If true: The CoThread yielded. If a result was returned, it can be accessed with getResult. If false: the coThread ran to the end of it's function and is complete.
//	 * */
//	public boolean await() throws RuntimeException, UncheckedInterruptedException { return threadHolder.await(); }
//	
//	/** 
//	 * run CoThread and return a promise that completes when the CoThread is done running. If the CoThread is already running: just returns the promise.
//	 * @return Promise that resolved to the result of the run. If the CoThread completes: the promise will be rejected with a CoThreadCompleteException.
//	 * If any other Exception is thrown in the CoThread: the promise will be rejected with the thrown Exception.
//	 * */
//	public Promise<T> run() { return threadHolder.run(); }
//	
//	@Override
//	public void finalize() throws Exception {
//		threadHolder.closeForGarbageCollector();
//	}
//	
//	/**
//	 * Interrupts the thread. The thread will automatically be interrupted eventually when the CoThread is garbage collected,
//	 * but can interrupted manually by this method if desired. Calling more than once is safe and will do nothing.
//	 */
//	@Override
//	public void close(){
//		threadHolder.close();
//	}
//	
//	// o------------o
//	// | subclasses |
//	// o------------o
//	public static class Result<T>{
//		public final T value;
//		Result(T value) { this.value = value; }
//	}
//	
//	
//	private static class CoThreadHolder<T> implements AutoCloseable{
//		private Promise<T> promise = null;
//		private boolean threadPaused = false;
//		private boolean complete = false;
//		private boolean errored = false;
//		private boolean started = false;
//		private Thread thread = null;
//		private String name = null;
//		private T result = null;
//		private RuntimeException exception = null;
//		
//		/**
//		 * Completes the promise (if run has been called at some point). Returns a promise.
//		 * @returns Promise to be resolved when the CoThread yields.
//		 * If the CoThread completes instead of yielding: the promise will be rejected with CoThreadCompleteException.
//		 * If any other Exception is thrown in the CoThread. The promise will reject with that exception.
//		 */
//		private synchronized void CompletePromise() {
//			if (promise == null) {
//				return;
//			}
//			else if (errored) {
//				promise.reject(exception);
//			}
//			else if (complete){
//				promise.reject(new CoThreadCompleteException());
//			}
//			else {
//				promise.resolve(result);
//			}
//			promise = null;
//		}
//		
//		public String getName() { return name; }
//		public T getResult() { return result; }
//		
//		
//		@SuppressWarnings("serial")
//		private static class YieldInterruptedException extends RuntimeException{}
//		
//		private synchronized void yield(T result) {
//			// handle result
//			this.result = result;
//			
//			// handle promises
//			CompletePromise();
//			
//			// wait for next await or get call
//			threadPaused = true;
//			notify();
//			try{
//				while(threadPaused){
//					wait();
//				}
//			}
//			catch(InterruptedException e){
//				throw new YieldInterruptedException();
//			}
//		}
//		
//		public boolean notComplete() { return !complete; }
//		public boolean isComplete() { return complete; }
//		public boolean isErrored() { return errored; }
//		public boolean started() { return started; }
//		
//		public CoThreadHolder(Consumer<Consumer<T>> routine, String name) {
//			if (routine == null) { throw new NullPointerException(); }
//			this.name = name;
//			
//			Runnable fullRoutine = () -> {
//				synchronized(this) {
//					try{
//						// wait for start() call
//						while(threadPaused){
//							wait();
//						}
//					
//						// start was called, wait for first await or get.
//						threadPaused = true;
//						notify();
//						while(threadPaused) {
//							wait();
//						}
//					}
//					catch(InterruptedException e) {
//						// interruption while waiting for first await or get
//						complete = true;
//						CompletePromise();
//						notify();
//						return;
//					}
//					
//					// start routine
//					try {
//						routine.accept(r -> this.yield(r));
//					}
//					catch(YieldInterruptedException e) {}
//					catch(RuntimeException e) {
//						threadPaused = true;
//						complete = true;
//						errored = true;
//						exception = e;
//						CompletePromise();
//						notify();
//						return;
//					}
//					
//					// routine is complete (or interrupted)
//					complete = true;
//					CompletePromise();
//					notify();
//				}
//			};
//			if (name != null) {
//				thread = new Thread(fullRoutine, "CoThread-" + name);
//			}
//			else {
//				thread = new Thread(fullRoutine, "CoThread");
//			}
//		}
//		
//		public CoThreadHolder(Consumer<Consumer<T>> routine) {
//			this(routine, null);
//		}
//		
//		
//		public synchronized void start(){
//			if (started) {
//				return;
//			}
//			
//			try {
//				thread.start();
//				started = true;
//				
//				threadPaused = false;
//				notify();
//				while(!threadPaused && !complete){
//					wait();
//				}
//			}
//			catch(InterruptedException e) {
//				throw new UncheckedInterruptedException(e);
//			}
//		}
//		
//		public static class CoThreadNotStartedException extends RuntimeException{
//			private static final long serialVersionUID = 1L;
//			
//			public CoThreadNotStartedException(){
//				super("CoThread was not started yet.");
//			}
//		}
//		
//		public synchronized Promise<T> run() {
//			if (!started()) { throw new CoThreadNotStartedException(); }
//			
//			threadPaused = false;
//			notify();
//			
//			// don't wait, return a promise instead
//			if (promise == null) { promise = new Promise<>(); }
//			
//			if (complete) {
//				promise.reject(new CoThreadCompleteException());
//			}
//			return promise;
//			
//		}
//		
//		public synchronized boolean await() throws RuntimeException, UncheckedInterruptedException {
//			if (!started()) { throw new CoThreadNotStartedException(); }
//			
//			threadPaused = false;
//			notify();
//			try {
//				while(!threadPaused && !complete) {
//					wait();
//				}
//			}
//			catch (InterruptedException e) {
//				throw new UncheckedInterruptedException(e);
//			}
//			
//			if (complete) {
//				if (errored) {
//					throw exception;
//				}
//				return false;
//			}
//			else {
//				return true;
//			}
//		}
//		
//		
//		@Override
//		public synchronized void close() {
//			thread.interrupt();
//		}
//		
//		// for garbage collector
//		private void closeForGarbageCollector() {
//			thread.interrupt();
//		}
//	}
//}
