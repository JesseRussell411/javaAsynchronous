package asyncronous;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


class CoThreadHolder<T> implements AutoCloseable{
	private T result = null;
	private Thread thread = null;
	private AtomicBoolean complete = new AtomicBoolean(false);
	private AtomicBoolean threadPaused = new AtomicBoolean(false);
	private AtomicBoolean started = new AtomicBoolean(false);
	@SuppressWarnings("serial")
	private static class YieldInterruptedException extends RuntimeException{}
	
	private synchronized void yield(T result) {
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
	
	
	public synchronized void start() throws InterruptedException {
		if (started()) {
			return;
		}
		
		thread.start();
		started.set(true);
		
		threadPaused.set(false);
		notify();
		while(!threadPaused.get() && !complete.get()){
			wait();
		}
	}
	
	public static class CoThreadNotStartedException extends RuntimeException{
		private static final long serialVersionUID = 1L;
		
		public CoThreadNotStartedException(){
			super("CoThread was not started yet.");
		}
	}
	
	public synchronized T await() throws InterruptedException{
		if (!started()) { throw new CoThreadNotStartedException(); }
		if (complete.get()) { return null; }
		
		
		T result = this.result;
		
		
		threadPaused.set(false);
		notify();
		while(!threadPaused.get() && !complete.get()){
			wait();
		}
		
		return result;
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


public class CoThread<T> implements AutoCloseable {
	private CoThreadHolder<T> threadHolder;
	public boolean notComplete() { return threadHolder.notComplete(); }
	public boolean complete() { return threadHolder.complete(); }
	public boolean started() { return threadHolder.started(); }
	
	public CoThread(Consumer<Consumer<T>> routine) { threadHolder = new CoThreadHolder<>(routine); }
	
	public CoThread<T> start() throws InterruptedException { 
		threadHolder.start();
		return this;
	}
	
	public T await() throws InterruptedException { return threadHolder.await(); }
	
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
}
