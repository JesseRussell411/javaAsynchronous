package asynchronous;

import java.util.function.Consumer;
import java.util.function.Supplier;


public class CoThread<T> implements AutoCloseable, Supplier<Promise<Boolean>> {
	private CoThreadHolder<T> threadHolder;
	public boolean notComplete() { return threadHolder.notComplete(); }
	public boolean isComplete() { return threadHolder.isComplete(); }
	public boolean isErrored() { return threadHolder.isErrored(); }
	public boolean started() { return threadHolder.started(); }
	public boolean closed() { return threadHolder.closed(); }
	public String getName() { return threadHolder.getName(); }
	public T getResult() { return threadHolder.getResult(); }
	
	public CoThread(Consumer<Consumer<T>> routine, String name) { threadHolder = new CoThreadHolder<>(routine, name); }
	public CoThread(Consumer<Consumer<T>> routine) { threadHolder = new CoThreadHolder<>(routine); }
	
	public CoThread<T> start() { 
		threadHolder.start();
		return this;
	}
	
	public boolean await() { return threadHolder.await(); }
	public Promise<Boolean> get() { return threadHolder.get(); }
	
	@Override
	public void finalize() throws Exception {
		threadHolder.closeForGarbageCollector();
	}
	
	/**
	 * Interrupts the thread. The thread will automatically be interrupted eventually when the CoThread is garbage collected,
	 * but can interrupted manually by this method if desired. Calling more than once is safe and will do nothing.
	 */
	@Override
	public void close() throws Exception{
		threadHolder.close();
	}
	
	// o------------o
	// | subclasses |
	// o------------o
	public static class Result<T>{
		public final T value;
		Result(T value) { this.value = value; }
	}
	
	
	private static class CoThreadHolder<T> implements AutoCloseable{
		private Promise<Boolean> promise = null;
		private boolean threadPaused = false;
		private boolean complete = false;
		private boolean errored = false;
		private boolean started = false;
		private boolean closed = false;
		private Thread thread = null;
		private String name = null;
		private T result = null;
		private RuntimeException exception = null;
		
		public String getName() { return name; }
		public T getResult() { return result; }
		
		
		@SuppressWarnings("serial")
		private static class YieldInterruptedException extends RuntimeException{}
		
		private synchronized void yield(T result) {
			// handle result
			if (promise != null) { promise.resolve(true); }
			this.result = result;
			
			// wait for next await or get call
			threadPaused = true;
			notify();
			try{
				while(threadPaused){
					wait();
				}
			}
			catch(InterruptedException e){
				throw new YieldInterruptedException();
			}
		}
		
		public boolean notComplete() { return !complete; }
		public boolean isComplete() { return complete; }
		public boolean isErrored() { return errored; }
		public boolean started() { return started; }
		public boolean closed() { return closed; }
		
		public CoThreadHolder(Consumer<Consumer<T>> routine, String name) {
			if (routine == null) { throw new NullPointerException(); }
			this.name = name;
			
			Runnable fullRoutine = () -> {
				synchronized(this) {
					try{
						// wait for start() call
						while(threadPaused){
							wait();
						}
					
						// start was called, wait for first await or get.
						threadPaused = true;
						notify();
						while(threadPaused) {
							wait();
						}
					}
					catch(InterruptedException e) {
						// interruption while waiting for first await or get
						complete = true;
						if (promise != null) { promise.resolve(false); }
						notify();
						return;
					}
					
					// start routine
					try {
						routine.accept(r -> this.yield(r));
					}
					catch(YieldInterruptedException e) {}
					catch(RuntimeException e) {
						errored = true;
						exception = e;
						complete = true;
						if (promise != null) {
							promise.reject(e);
						}
						notify();
						return;
					}
					
					// routine is complete (or interrupted)
					complete = true;
					if (promise != null) { promise.resolve(false); }
					notify();
				}
			};
			if (name != null) {
				thread = new Thread(fullRoutine, "CoThread-" + name);
			}
			else {
				thread = new Thread(fullRoutine, "CoThread");
			}
		}
		
		public CoThreadHolder(Consumer<Consumer<T>> routine) {
			this(routine, null);
		}
		
		
		public synchronized void start(){
			if (started) {
				return;
			}
			
			try {
				thread.start();
				started = true;
				
				threadPaused = false;
				notify();
				while(!threadPaused && !complete){
					wait();
				}
			}
			catch(InterruptedException e) {
				throw new UncheckedInterruptedException(e);
			}
		}
		
		public static class CoThreadNotStartedException extends RuntimeException{
			private static final long serialVersionUID = 1L;
			
			public CoThreadNotStartedException(){
				super("CoThread was not started yet.");
			}
		}
		
		public synchronized Promise<Boolean> get() {
			if (!started()) { throw new CoThreadNotStartedException(); }
			promise = new Promise<>();
			
			threadPaused = false;
			notify();
			
			// don't wait
			return promise;
		}
		
		public synchronized boolean await() throws RuntimeException, UncheckedInterruptedException {
			if (!started()) { throw new CoThreadNotStartedException(); }
			promise = null;
			
			threadPaused = false;
			notify();
			try {
				while(!threadPaused && !complete) {
					wait();
				}
			}
			catch (InterruptedException e) {
				throw new UncheckedInterruptedException(e);
			}
			
			if (complete) {
				if (errored) {
					throw exception;
				}
				return false;
			}
			else {
				return true;
			}
		}
		
		
		@Override
		public synchronized void close() throws Exception {
			if (!complete) {
				thread.interrupt();
				notify();
				while(!complete) {
					wait();
				}
				closed = true;
			}
		}
		
		// for garbage collector
		private void closeForGarbageCollector() {
			thread.interrupt();
		}
	}
}
