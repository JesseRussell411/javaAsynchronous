package asynchronous.asyncAwait;
import java.util.function.*;

import asynchronous.Promise;

public class AsyncVoid implements Supplier<Promise<Object>>{
	private Async<Object> async;
	
	public AsyncVoid(Consumer<Async.Await> func, String name) {
		async = new Async<Object>(
				await -> { func.accept(await); return null; }, name);
	}
	
	public AsyncVoid(Consumer<Async.Await> func) {
		this(func, null);
	}
	
	public synchronized Promise<Object> get(){
		return async.get();
	}
	
	public String getName() {
		return async.getName();
	}
}
