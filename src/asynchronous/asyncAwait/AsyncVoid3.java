package asynchronous.asyncAwait;
import functionPlus.*;


import asynchronous.Promise;

public class AsyncVoid3<T1, T2, T3> implements TriFunction<T1, T2, T3, Promise<Object>>{
	private Async3<T1, T2, T3, Object> async;
	
	public AsyncVoid3(QuadConsumer<Async.Await, T1, T2, T3> func, String name) {
		async = new Async3<T1, T2, T3, Object>(
				(await, t1, t2, t3) -> { func.accept(await, t1, t2, t3); return null; }, name);
	}
	
	public AsyncVoid3(QuadConsumer<Async.Await, T1, T2, T3> func) {
		this(func, null);
	}
	
	public synchronized Promise<Object> apply(T1 t1, T2 t2, T3 t3) {
		return async.apply(t1, t2, t3);
	}
	
	public String getName() {
		return async.getName();
	}
}
