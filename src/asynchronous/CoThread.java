package asynchronous;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;





public class CoThread<T> implements AutoCloseable {
	private CoThreadHolder<T> threadHolder;
	public boolean notComplete() { return threadHolder.notComplete(); }
	public boolean complete() { return threadHolder.complete(); }
	public boolean started() { return threadHolder.started(); }
	
	public CoThread(Consumer<Consumer<T>> routine) { threadHolder = new CoThreadHolder<>(routine); }
	
	public CoThread<T> start() { 
		threadHolder.start();
		return this;
	}
	
	public T await() { return threadHolder.await(); }
	public Promise<T> get() { return threadHolder.get(); }
	
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
	
	public static class Result<T>{
		public final T value;
		Result(T value) { this.value = value; }
	}
	
	private static class CoThreadHolder<T> implements AutoCloseable{
		private AtomicBoolean threadPaused = new AtomicBoolean(false);
		private AtomicBoolean complete = new AtomicBoolean(false);
		private AtomicBoolean started = new AtomicBoolean(false);
		private Promise<T> promise = null;
		private Thread thread = null;
		private T result = null;
		
		@SuppressWarnings("serial")
		private static class YieldInterruptedException extends RuntimeException{}
		
		private synchronized void yield(T result) {
			if (promise != null) { promise.resolve(this.result); }
			
			this.result = result;
			threadPaused.set(true);
			notify();
			try{
				while(threadPaused.get()){
					wait();
				}
			}
			catch(InterruptedException e){
				throw new YieldInterruptedException();
			}
		}
		
		
		public boolean notComplete() { return !complete.get(); }
		public boolean complete() { return complete.get(); }
		public boolean started() { return started.get(); }
		
		
		public CoThreadHolder(Consumer<Consumer<T>> routine) {
			if (routine == null) { throw new NullPointerException(); }
			
			thread = new Thread(() -> {
				synchronized(this) {
					// wait for start() call
					try{
						while(threadPaused.get()){
							wait();
						}
					} catch(InterruptedException e){
						complete.set(true);
						notify();
						return;
					}
					
					// start routine
					try {
						routine.accept(r -> this.yield(r));
					}
					catch(YieldInterruptedException e) {}
					
					// routine is complete (or interrupted)
					complete.set(true);
					notify();
				}
			});
		}
		
		
		public synchronized void start(){
			if (started()) {
				return;
			}
			
			try {
				thread.start();
				started.set(true);
				
				threadPaused.set(false);
				notify();
				while(!threadPaused.get() && !complete.get()){
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
		
		public synchronized Promise<T> get(){
			if (!started()) { throw new CoThreadNotStartedException(); }
			promise = new Promise<>();
			
			threadPaused.set(false);
			notify();
			
			// don't wait
			return promise;
		}
		
		public synchronized T await() {
			if (!started()) { throw new CoThreadNotStartedException(); }
			if (complete.get()) { return null; }
			
			
			T result = this.result;
			
			try {
				threadPaused.set(false);
				notify();
				while(!threadPaused.get() && !complete.get()){
					wait();
				}
				
				return result;
			}
			catch(InterruptedException e) {
				throw new UncheckedInterruptedException(e);
			}
		}
		
		
		@Override
		public synchronized void close() throws Exception {
			if (!complete.get()) {
				thread.interrupt();
				notify();
				while(!complete.get()) {
					wait();
				}
			}
		}
		
		void closeWithoutWait() {
			thread.interrupt();
		}
	}
}
