package asynchronous.asyncAwait;
import java.util.function.*;

import asynchronous.Promise;

public class Async1Void<T1> implements Function<T1, Promise<Void>>{
	private final Async1<T1, Void> async;
	
	public Async1Void(BiConsumer<Async.Await, T1> func, String name) {
		async = new Async1<T1, Void>(
				(await, t1) -> { func.accept(await, t1); return null; }, name);
	}
	
	public Async1Void(BiConsumer<Async.Await, T1> func) {
		this(func, null);
	}
	
	public synchronized Promise<Void> apply(T1 t1) {
		return async.apply(t1);
	}
	
	public String getName() {
		return async.getName();
	}
}
