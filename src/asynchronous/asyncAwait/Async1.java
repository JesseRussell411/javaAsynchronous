package asynchronous.asyncAwait;
import java.util.function.*;

import asynchronous.Promise;
public class Async1<T0, R> {
	private Async<R> async;
	private Object[] args = new Object[1];
	public Async1(BiFunction<Async.Await, T0, R> func, String name) {
		
		async = new Async<R>(
				await -> func.apply(await, (T0)args[0]) , name);
	}
	
	public Async1(BiFunction<Async.Await, T0, R> func) {
		this(func, null);
	}
	
	public synchronized Promise<R> get(T0 t0){
		args[0] = t0;
		return async.get();
	}
	public String getName() {
		return async.getName();
	}
}
