package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;

public class AsyncVoid5<T1, T2, T3, T4, T5> implements PentaFunction<T1, T2, T3, T4, T5, Promise<Object>>{
	private final Async5<T1, T2, T3, T4, T5, Object> async;
	
	public AsyncVoid5(HexaConsumer<Async.Await, T1, T2, T3, T4, T5> func, String name) {
		async = new Async5<T1, T2, T3, T4, T5, Object>(
				(await, t1, t2, t3, t4, t5) -> { func.accept(await, t1, t2, t3, t4, t5); return null; }, name);
	}
	
	public AsyncVoid5(HexaConsumer<Async.Await, T1, T2, T3, T4, T5> func) {
		this(func, null);
	}
	
	public synchronized Promise<Object> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
		return async.apply(t1, t2, t3, t4, t5);
	}
	
	public String getName() {
		return async.getName();
	}
}
