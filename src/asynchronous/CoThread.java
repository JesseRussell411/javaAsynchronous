package asynchronous;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;





public class CoThread<T> implements AutoCloseable {
	private CoThreadHolder<T> threadHolder;
	public boolean notComplete() { return threadHolder.notComplete(); }
	public boolean isComplete() { return threadHolder.isComplete(); }
	public boolean started() { return threadHolder.started(); }
	public String getName() { return threadHolder.getName(); }
	
	public CoThread(Consumer<Consumer<T>> routine, String name) { threadHolder = new CoThreadHolder<>(routine, name); }
	public CoThread(Consumer<Consumer<T>> routine) { threadHolder = new CoThreadHolder<>(routine); }
	
	public CoThread<T> start() { 
		threadHolder.start();
		return this;
	}
	
	public Result<T> await() { return threadHolder.await(); }
	public Promise<Result<T>> get() { return threadHolder.get(); }
	
	@Override
	public void finalize() throws Exception {
		threadHolder.closeWithoutWait();
	}
	
	/**
	 * Interrupts the thread. The thread will automatically be interrupted eventually when the CoThread is garbage collected, but can interrupted manually by this method if desired. Calling more than once is safe and will do nothing.
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
		private boolean threadPaused = false;
		private Promise<Result<T>> promise = null;
		private boolean complete = false;
		private boolean started = false;
		private Thread thread = null;
		private String name = null;
		private T result = null;
		
		public String getName() { return name; }
		
		
		@SuppressWarnings("serial")
		private static class YieldInterruptedException extends RuntimeException{}
		
		private synchronized void yield(T result) {
			// handle result
			if (promise != null) { promise.resolve(new Result<T>(this.result)); }
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
		public boolean started() { return started; }
		
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
						complete = true;
						if (promise != null) { promise.resolve(null); }
						notify();
						return;
					}
					
					// start routine
					try {
						routine.accept(r -> this.yield(r));
					}
					catch(YieldInterruptedException e) {}
					
					// routine is complete (or interrupted)
					complete = true;
					if (promise != null) { promise.resolve(null); }
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
		
		public synchronized Promise<Result<T>> get() {
			if (!started()) { throw new CoThreadNotStartedException(); }
			promise = new Promise<>();
			
			threadPaused = false;
			notify();
			
			// don't wait
			return promise;
		}
		
		public synchronized Result<T> await() {
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
				return null;
			}
			else {
				return new Result<T>(result);
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
			}
		}
		
		// for garbage collector
		void closeWithoutWait() {
			thread.interrupt();
		}
	}
}
