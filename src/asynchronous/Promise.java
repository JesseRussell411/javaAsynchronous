package asynchronous;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import reference.*;

interface Callback<T, R>{
	boolean applyResolve(T result);
	boolean applyReject(Throwable error);
	Promise<R> getNext();
}

class SyncCallback<T, R> implements Callback<T, R>{
	Function<T, R> then;
	Function<Throwable, R> catcher;
	Promise<R> next;
	
	SyncCallback(Function<T, R> then, Function<Throwable, R> catcher){
		this.next = new Promise<R>();
		this.then = then;
		if (catcher != null)
			this.catcher = catcher;
		else
			this.catcher = e -> {next.reject(e); return null;};
	}
	
	public Promise<R> getNext() { return next; }
	
	@Override
	public boolean applyResolve(T result) {
		synchronized(next) {
			if (next.isSettled()) {
				return false;
			}
			else {
				try {
					next.resolve(then.apply(result));
				}
				catch(Throwable e) {
					next.reject(e);
				}
				return true;
			}
		}
	}
	
	@Override
	public boolean applyReject(Throwable error){
		synchronized(next) {
			if (next.isSettled()) {
				return false;
			}
			else {
				try {
					next.resolve(catcher.apply(error));
				}
				catch(Throwable e) {
					next.reject(e);
				}
				return true;
			}
		}
	}
}

class AsyncCallback<T, R> implements Callback<T, R>{
	Function<T, R> then;
	Function<Throwable, R> catcher;
	Promise<R> next;
	
	AsyncCallback(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher){
		this.next = new Promise<R>();
		this.then = then;
		if (catcher != null)
			this.catcher = catcher;
		else
			this.catcher = e -> {next.reject(e); return null;};
	}
	
	public Promise<R> getNext() { return next; }
	
	@Override
	public boolean applyResolve(T result) {
		synchronized(next) {
			if (next.isSettled()) {
				return false;
			}
			else {
				then.apply(result).thenAccept(r -> next.resolve(r), e -> next.reject(e));
				return true;
			}
		}
	}
	
	@Override
	public boolean applyReject(Throwable error){
		synchronized(next) {
			if (next.isSettled()) {
				return false;
			}
			else {
				catcher.apply(error).thenAccept(r -> next.resolve(r), e -> next.reject(e));
				return true;
			}
		}
	}
}

public class Promise<T>{
	private T result = null;
	private Throwable error = null;
	private boolean fulfilled;
	private boolean rejected;
	
	public boolean isPending() { return !fulfilled && !rejected; }
	public boolean isSettled() { return fulfilled || rejected; }
	public boolean isFulfilled() { return fulfilled; }
	public boolean isRejected() { return rejected; }
	
	public T getResult() { return result; }
	public Throwable getError() { return error; }
	
	
	synchronized boolean resolve(T result) {
		if (isSettled()) {
			return false;
		}
		else {
			fulfilled = true;
			this.result = result;
			
			resolveCallbacks();
			
			return true;
		}
	}
	synchronized boolean reject(Throwable error) {
		if (isSettled()) {
			return false;
		}
		else{
			rejected = true;
			this.error = error;
			
			rejectCallbacks();
			
			return true;
		}
	}
	
	Promise(){}
	
	public Promise(BiConsumer<Function<T, Boolean>, Function<Throwable, Boolean>> initializer) {
		initializer.accept(t -> resolve(t), e -> reject(e));
	}
	
	private Queue<Callback<T, ?>> callbacks = new LinkedList<Callback<T, ?>>();
	
	private synchronized void resolveCallbacks() {
		Callback<T, ?> current;
		while((current = callbacks.poll()) != null) {
			current.applyResolve(result);
		}
	}
	private synchronized void rejectCallbacks() {
		Callback<T, ?> current;
		while((current = callbacks.poll()) != null) {
			current.applyReject(error);
		}
	}
	
	private synchronized <R> Promise<R> thenApply(Callback<T, R> callback){
		callbacks.add(callback);
		
		if (isFulfilled())
			resolveCallbacks();
		else if (isRejected())
			rejectCallbacks();
		
		return callback.next;
	}
	
	// then and error
	public synchronized <R> Promise<R> thenApply(Function<T, R> then, Function<Throwable, R> catcher){
		return thenApply(new SyncCallback<T, R>(then, catcher));
	}
	
	public synchronized Promise<Void> thenAccept(Consumer<T> then, Consumer<Throwable> catcher){
		return thenApply(t -> {
			then.accept(t);
			return null;
		}, e -> {
			catcher.accept(e);
			return null;
		});
	}
	
	public synchronized <R> Promise<R> thenGet(Supplier<R> then, Supplier<R> catcher){
		return thenApply(t -> then.get(), e -> catcher.get());
	}
	
	public synchronized Promise<Void> thenRun(Runnable then, Runnable catcher){
		return thenApply(t -> {
			then.run();
			return null;
		}, e -> {
			catcher.run();
			return null;
		});
	}
	
	
	public synchronized <R> Promise<R> asyncThenApply(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher){
		return thenApply(new AsyncCallback<T, R>(then, catcher));
	}
	
	public synchronized <R> Promise<R> asyncThenGet(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher){
		return asyncThenApply(t -> then.get(), e -> catcher.get());
	}
	
	// just then
	public synchronized <R> Promise<R> thenApply(Function<T, R> then){
		return thenApply(then, null);
	}
	
	public synchronized Promise<Void> thenAccept(Consumer<T> then){
		return thenApply(t -> {
			then.accept(t);
			return null;
		});
	}
	
	public synchronized <R> Promise<R> thenGet(Supplier<R> then){
		return thenApply(r -> then.get());
	}
	
	public synchronized Promise<Void> thenRun(Runnable then){
		return thenApply(t -> {
			then.run();
			return null;
		});
	}
	
	public synchronized <R> Promise<R> asyncThenApply(Function<T, Promise<R>> then){
		return thenApply(new AsyncCallback<T, R>(then, null));
	}
	
	public synchronized <R>
	
	// on error
	public synchronized <R> Promise<R> onErrorApply(Function<Throwable, R> catcher){
		return thenApply(null, catcher);
	}
	
	public synchronized Promise<Void> onErrorAccept(Consumer<Throwable> catcher) {
		return onErrorApply(e -> {
			catcher.accept(e);
			return null;
		});
	}
	
	public synchronized <R> Promise<R> onErrorGet(Supplier<R> catcher){
		return onErrorApply(e -> catcher.get());
	}
	
	public synchronized Promise<Void> onErrorRun(Runnable catcher){
		return onErrorApply(e -> {
			catcher.run();
			return null;
		});
	}
	
	// on settled
	public synchronized <R> Promise<R> onSettledGet(Supplier<R> settler){
		return thenApply(t -> settler.get(), t -> settler.get());
	}
	
	public synchronized Promise<Void> onSettledRun(Runnable settler){
		return onSettledGet(() ->{
			settler.run();
			return null;
		})
	}
}
