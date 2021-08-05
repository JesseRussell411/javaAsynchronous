package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;
public class Async3<T1, T2, T3, R> {
	private Async<R> async;
	private Object[] args = new Object[3];
	public Async3(QuadFunction<Async.Await, T1, T2, T3, R> func, String name) {
		async = new Async<R>(
				await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2]), name);
	}
	
	public Async3(QuadFunction<Async.Await, T1, T2, T3, R> func) {
		this(func, null);
	}
	
	public synchronized Promise<R> get(T1 t1, T2 t2, T3 t3){
		args[0] = t1;
		args[1] = t2;
		args[2] = t3;
		return async.get();
	}
	
	public String getName() {
		return async.getName();
	}
}
