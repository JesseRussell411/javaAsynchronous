package asynchronous.asyncAwait;
import functionPlus.*;
import java.util.function.*;

import asynchronous.Promise;

public class Async2Void<T1, T2> implements BiFunction<T1, T2, Promise<Void>>{
	private final Async2<T1, T2, Void> async;
	
	public Async2Void(TriConsumer<Async.Await, T1, T2> func, String name) {
		async = new Async2<T1, T2, Void>(
				(await, t1, t2) -> { func.accept(await, t1, t2); return null; }, name);
	}
	
	public Async2Void(TriConsumer<Async.Await, T1, T2> func) {
		this(func, null);
	}
	
	public synchronized Promise<Void> apply(T1 t1, T2 t2) {
		return async.apply(t1, t2);
	}
	
	public String getName() {
		return async.getName();
	}
}
