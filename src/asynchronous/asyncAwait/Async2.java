package asynchronous.asyncAwait;
import functionPlus.*;
import java.util.function.*;

import asynchronous.Promise;

public class Async2<T1, T2, R> implements BiFunction<T1, T2, Promise<R>>{
	private Async<R> async;
	private Object[] args = new Object[2];
	
	public Async2(TriFunction<Async.Await, T1, T2, R> func, String name) {
		async = new Async<R>(
				await -> func.apply(await, (T1)args[0], (T2)args[1]), name);
	}
	
	public Async2(TriFunction<Async.Await, T1, T2, R> func) {
		this(func, null);
	}
	
	public synchronized Promise<R> apply(T1 t1, T2 t2){
		args[0] = t1;
		args[1] = t2;
		return async.get();
	}
	
	public String getName() {
		return async.getName();
	}
}
