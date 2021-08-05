package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;
public class Async5<T1, T2, T3, T4, T5, R> {
	private Async<R> async;
	private Object[] args = new Object[5];
	public Async5(HexaFunction<Async.Await, T1, T2, T3, T4, T5, R> func, String name) {
		async = new Async<R>(
				await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3], (T5)args[4]), name);
	}
	
	public Async5(HexaFunction<Async.Await, T1, T2, T3, T4, T5, R> func) {
		this(func, null);
	}
	
	public synchronized Promise<R> get(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5){
		args[0] = t1;
		args[1] = t2;
		args[2] = t3;
		args[3] = t4;
		args[4] = t5;
		return async.get();
	}
	
	public String getName() {
		return async.getName();
	}
}
