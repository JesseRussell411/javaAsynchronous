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

public class Async<T> {
	private static Map<Promise<Object>, Async<Object>.Instance> promises = new ConcurrentHashMap<>();
	private static Queue<Async<Object>.Instance> executionQueue = new ConcurrentLinkedQueue<>();
	private static Map<Async<Object>.Instance, Promise<Object>> waitingOn = new ConcurrentHashMap<>();
	private Function<Await<T>, T> func;
	
	public Async(Function<Await<T>, T> func) {
		this.func = func;
	}
	
	public Promise<T> call(){
		var inst = new Instance(func);
		return inst.execute();
	}
	
	public static void execute() throws InterruptedException{
		Thread promiseWatcher = new Thread(() -> {
			try {
				while(true) {
					for(var promise : promises.keySet()) {
						if (promise.isResolved()) {
							executionQueue.add(promises.get(promise));
							promises.remove(promise);
						}
					}
					Thread.sleep(1);
				}
			}
			catch(InterruptedException e) {}
		});
		
		promiseWatcher.start();
		
		while(true) {
			while(!executionQueue.isEmpty()) {
				Async<Object>.Instance inst = executionQueue.poll();
				if (inst.coThread.notComplete()) {
					if (waitingOn.containsKey(inst)) {
						promises.put(waitingOn.get(inst), inst);
						waitingOn.remove(inst);
					}
					else {
						promises.put(inst.coThread.await(), inst);
					}
				}
				else {
					inst.resolve.accept(inst.result);
				}
			}
			if (!promises.isEmpty()) {
				Thread.sleep(1);
			}
			else {
				break;
			}
		}
	}
	
	
	
	public class Instance{
		CoThread<Promise<T>> coThread;
		T result;
		Promise<T> promise;
		Consumer<T> resolve;
		
		Instance(Function<Await<T>, T> func){
			coThread = new CoThread<>(yield -> {
				result = func.apply(new Await<T>(yield, this));
			});
		}
		
		public Promise<T> execute(){
			coThread.start();
			promise = new Promise<T>(resolve -> this.resolve = resolve);
			executionQueue.add((Async<Object>.Instance)this);
			return promise;
		}
	}
	
	public static class Await<T>{
		private Consumer<Promise<T>> yield;
		private T result;
		private Async<T>.Instance instance;
		
		public Await(Consumer<Promise<T>> yield, Async<T>.Instance isntance) {
			this.instance = isntance;
			this.yield = yield;
		}
		public T apply(Promise<T> promise) {
			promise.then(r -> {result = r;});
			waitingOn.put((Async<Object>.Instance)instance, (Promise<Object>)promise);
			yield.accept(promise);
			return result;
		}
	}
}
