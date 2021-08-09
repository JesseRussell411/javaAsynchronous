package asynchronous.asyncAwait;
import java.util.function.*;

import asynchronous.Promise;

public class Async1<T1, R> implements Function<T1, Promise<R>>{
	private final Async<R> async;
	private final Object[] args = new Object[1];
	
	public Async1(BiFunction<Async.Await, T1, R> func, String name) {
		async = new Async<R>(
				await -> func.apply(await, (T1)args[0]), name);
	}
	
	public Async1(BiFunction<Async.Await, T1, R> func) {
		this(func, null);
	}
	
	public synchronized Promise<R> apply(T1 t1){
		args[0] = t1;
		return async.get();
	}
	
	public String getName() {
		return async.getName();
	}
}
