package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;

public class Async7Void<T1, T2, T3, T4, T5, T6, T7> implements HeptaFunction<T1, T2, T3, T4, T5, T6, T7, Promise<Object>>{
	private final Async7<T1, T2, T3, T4, T5, T6, T7, Object> async;
	
	public Async7Void(OctoConsumer<Async.Await, T1, T2, T3, T4, T5, T6, T7> func, String name) {
		async = new Async7<T1, T2, T3, T4, T5, T6, T7, Object>(
				(await, t1, t2, t3, t4, t5, t6, t7) -> { func.accept(await, t1, t2, t3, t4, t5, t6, t7); return null; }, name);
	}
	
	public Async7Void(OctoConsumer<Async.Await, T1, T2, T3, T4, T5, T6, T7> func) {
		this(func, null);
	}
	
	public synchronized Promise<Object> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7) {
		return async.apply(t1, t2, t3, t4, t5, t6, t7);
	}
	
	public String getName() {
		return async.getName();
	}
}
