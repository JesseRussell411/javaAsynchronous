package asynchronous.asyncAwait;
import java.util.function.*;

import asynchronous.Promise;

public class AsyncVoid implements Supplier<Promise<Void>>{
	private final Async<Void> async;
	
	public AsyncVoid(Consumer<Async.Await> func, String name) {
		async = new Async<Void>(
				await -> { func.accept(await); return null; }, name);
	}
	
	public AsyncVoid(Consumer<Async.Await> func) {
		this(func, null);
	}
	
	public synchronized Promise<Void> get(){
		return async.get();
	}
	
	public String getName() {
		return async.getName();
	}
}
