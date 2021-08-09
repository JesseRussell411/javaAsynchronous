package asynchronous.asyncAwait;
import functionPlus.*;
import java.util.function.*;

import asynchronous.Promise;

public class AsyncVoid2<T1, T2> implements BiFunction<T1, T2, Promise<Object>>{
	private final Async2<T1, T2, Object> async;
	
	public AsyncVoid2(TriConsumer<Async.Await, T1, T2> func, String name) {
		async = new Async2<T1, T2, Object>(
				(await, t1, t2) -> { func.accept(await, t1, t2); return null; }, name);
	}
	
	public AsyncVoid2(TriConsumer<Async.Await, T1, T2> func) {
		this(func, null);
	}
	
	public synchronized Promise<Object> apply(T1 t1, T2 t2) {
		return async.apply(t1, t2);
	}
	
	public String getName() {
		return async.getName();
	}
}
