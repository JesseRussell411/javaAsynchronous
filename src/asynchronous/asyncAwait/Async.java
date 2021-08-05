package asynchronous.asyncAwait;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.*;

import asynchronous.CoThread;
import asynchronous.Promise;
import asynchronous.UncheckedInterruptedException;

public class Async<T> implements Supplier<Promise<T>>{
	private static Map<Promise<Object>, Async<Object>.Instance> promises = new ConcurrentHashMap<>();
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
	
	public static void execute() throws InterruptedException{
		
		// start new thread to look out for complete promises so that their corresponding async.isntances can be added back onto the executionQueue
		Thread promiseWatcher = new Thread(() -> {
			try {
				while(true) {
					for(Promise<Object> promise : promises.keySet()) {
						if (promise.isResolved()) {
							// a promise is complete, add it's instance back onto the queue and remove it from promises
							executionQueue.add(promises.get(promise));
							promises.remove(promise);
						}
					}
					Thread.sleep(1);
				}
			}
			catch(InterruptedException e) {}
			// thread interrupted. time to stop watching for promises.
		}, "Async-promiseWatcher");
		
		promiseWatcher.start();
		
		// execution loop
		while(true) {
			while(!executionQueue.isEmpty()) {
				// take next async.instance off queue.
				Async<Object>.Instance instance = executionQueue.poll();
				
				// run instance until next yield or completion
				var awaitResult = instance.coThread.await();
				
				// was it a yield or completion?
				if (awaitResult != null) {
					// yield
					promises.put(awaitResult.value, instance);
				}
				else {
					// completion
					instance.resolve.accept(instance.result);
				}
			}
			
			// execution queue is empty. Check if there are promises being waited on.
			if (!promises.isEmpty()) {
				// if so, wait for some time, then start the loop over again.
				Thread.sleep(1);
			}
			else {
				// if not, stop the loop. execution is complete.
				break;
			}
		}
		
		// stop the promise watcher
		promiseWatcher.interrupt();
	}
	
	
	
	public class Instance{
		CoThread<Promise<Object>> coThread;
		T result;
		Promise<T> promise;
		Consumer<T> resolve;
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
			promise = new Promise<T>(resolve -> this.resolve = resolve);
			executionQueue.add((Async<Object>.Instance)this);
			return promise;
		}
	}
	
	public static class Await{
		private Consumer<Promise<Object>> yield;
		private Object result;
		private Async<Object>.Instance instance;
		
		public Await(Consumer<Promise<Object>> yield, Async<Object>.Instance isntance) {
			this.instance = isntance;
			this.yield = yield;
		}
		
		public <E> E apply(Promise<E> promise) {
			promise.then(r -> {result = r;});
			yield.accept((Promise<Object>)promise);
			return (E)result;
		}
	}
}
