package asynchronous.asyncAwait;
import functionPlus.*;


import asynchronous.Promise;

public class AsyncVoid6<T1, T2, T3, T4, T5, T6> implements HexaFunction<T1, T2, T3, T4, T5, T6, Promise<Object>>{
	private Async6<T1, T2, T3, T4, T5, T6, Object> async;
	
	public AsyncVoid6(HeptaConsumer<Async.Await, T1, T2, T3, T4, T5, T6> func, String name) {
		async = new Async6<T1, T2, T3, T4, T5, T6, Object>(
				(await, t1, t2, t3, t4, t5, t6) -> { func.accept(await, t1, t2, t3, t4, t5, t6); return null; }, name);
	}
	
	public AsyncVoid6(HeptaConsumer<Async.Await, T1, T2, T3, T4, T5, T6> func) {
		this(func, null);
	}
	
	public synchronized Promise<Object> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
		return async.apply(t1, t2, t3, t4, t5, t6);
	}
	
	public String getName() {
		return async.getName();
	}
}
