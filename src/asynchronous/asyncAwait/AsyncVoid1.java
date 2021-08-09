package asynchronous.asyncAwait;
import java.util.function.*;

import asynchronous.Promise;

public class AsyncVoid1<T1> implements Function<T1, Promise<Object>>{
	private final Async1<T1, Object> async;
	
	public AsyncVoid1(BiConsumer<Async.Await, T1> func, String name) {
		async = new Async1<T1, Object>(
				(await, t1) -> { func.accept(await, t1); return null; }, name);
	}
	
	public AsyncVoid1(BiConsumer<Async.Await, T1> func) {
		this(func, null);
	}
	
	public synchronized Promise<Object> apply(T1 t1) {
		return async.apply(t1);
	}
	
	public String getName() {
		return async.getName();
	}
}
