package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;

public class Async4Void<T1, T2, T3, T4> implements QuadFunction<T1, T2, T3, T4, Promise<Void>>{
	private final Async4<T1, T2, T3, T4, Void> async;
	
	public Async4Void(PentaConsumer<Async.Await, T1, T2, T3, T4> func, String name) {
		async = new Async4<T1, T2, T3, T4, Void>(
				(await, t1, t2, t3, t4) -> { func.accept(await, t1, t2, t3, t4); return null; }, name);
	}
	
	public Async4Void(PentaConsumer<Async.Await, T1, T2, T3, T4> func) {
		this(func, null);
	}
	
	public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4) {
		return async.apply(t1, t2, t3, t4);
	}
	
	public String getName() {
		return async.getName();
	}
}
