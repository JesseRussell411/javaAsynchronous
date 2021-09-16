package asynchronous;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import reference.RefBoolean;

public class Promise<T> implements Future<T>{
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
			
			notifyAll();
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
			
			notifyAll();
			return true;
		}
	}
	
	Promise(){}
	
	public Promise(BiConsumer<Function<T, Boolean>, Function<Throwable, Boolean>> initializer) {
		initializer.accept(t -> resolve(t), e -> reject(e));
	}
	
	public Promise(Consumer<Function<T, Boolean>> initializer) {
		initializer.accept(t -> resolve(t));
	}
	
	// callback stuff
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
		
		return callback.getNext();
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
	
	public synchronized <R> Promise<R> asyncThenGet(Supplier<Promise<R>> then){
		return asyncThenApply(t -> then.get());
	}
	
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
	
	public synchronized <R> Promise<R> asyncOnErrorApply(Function<Throwable, Promise<R>> catcher){
		return asyncThenApply(null, catcher);
	}
	
	public synchronized <R> Promise<R> asyncOnErrorGet(Supplier<Promise<R>> catcher){
		return asyncOnErrorApply(e -> catcher.get());
	}
	
	// on settled
	public synchronized <R> Promise<R> onSettledGet(Supplier<R> settler){
		return thenApply(t -> settler.get(), t -> settler.get());
	}
	
	public synchronized Promise<Void> onSettledRun(Runnable settler){
		return onSettledGet(() ->{
			settler.run();
			return null;
		});
	}
	
	public synchronized <R> Promise<R> asyncOnSettledGet(Supplier<Promise<R>> settler){
		return asyncThenApply(t -> settler.get(), e -> settler.get());
	}
	
	// auto-FunctionType versions:
	// then and error
	public synchronized <R> Promise<R> then(Function<T, R> then, Function<Throwable, R> catcher){return thenApply(then, catcher);}
	public synchronized Promise<Void>  then(Consumer<T> then, Consumer<Throwable> catcher){return thenAccept(then, catcher);}
	public synchronized <R> Promise<R> then(Supplier<R> then, Supplier<R> catcher){return thenGet(then, catcher);}
	public synchronized Promise<Void>  then(Runnable then, Runnable catcher){return thenRun(then, catcher);}
	public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher){return asyncThenApply(then, catcher);}
	public synchronized <R> Promise<R> asyncThen(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher){return asyncThenGet(then, catcher);}
	
	// just then
	public synchronized <R> Promise<R> then(Function<T, R> then){return thenApply(then);}
	public synchronized Promise<Void>  then(Consumer<T> then){return thenAccept(then);}
	public synchronized <R> Promise<R> then(Supplier<R> then){return thenGet(then);}
	public synchronized Promise<Void>  then(Runnable then){return thenRun(then);}
	public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> then){return asyncThenApply(then);}
	public synchronized <R> Promise<R> AsyncThen(Supplier<Promise<R>> then){return asyncThenGet(then);}
	
	// on error
	public synchronized <R> Promise<R> onError(Function<Throwable, R> catcher){return onErrorApply(catcher);}
	public synchronized Promise<Void> onError(Consumer<Throwable> catcher) {return onErrorAccept(catcher);}
	public synchronized <R> Promise<R> onError(Supplier<R> catcher){return onErrorGet(catcher);}
	public synchronized Promise<Void> onError(Runnable catcher){return onErrorRun(catcher);}
	public synchronized <R> Promise<R> asyncOnError(Function<Throwable, Promise<R>> catcher){return asyncOnErrorApply(catcher);}
	public synchronized <R> Promise<R> asyncOnError(Supplier<Promise<R>> catcher){return asyncOnErrorGet(catcher);}
	
	// on settled
	public synchronized <R> Promise<R> onSettle(Supplier<R> settler){return onSettledGet(settler);}
	public synchronized Promise<Void> onSettled(Runnable settler){return onSettledRun(settler);}
	public synchronized <R> Promise<R> asyncOnSettled(Supplier<Promise<R>> settler){return asyncOnSettledGet(settler);}
	
	
	// blocking wait
	public synchronized T await(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException{
		if (isRejected())
			throw new ExecutionException(error);
		else if (isFulfilled())
			return result;
		
		final var timedOut = new RefBoolean(false);
		
		final var timerThread = new Thread(() -> {
			try {
				if (unit.toMillis(timeout) < Long.MAX_VALUE / 1000)
					Thread.sleep(unit.toMillis(timeout), (int)(unit.toNanos(timeout) % 1000));
				else
					Thread.sleep(unit.toMillis(timeout));
			}
			catch(InterruptedException ie) {}
			
			timedOut.set(true);
			notifyAll();
		});
		
		
		timerThread.start();
		
		while(isPending() &&  !timedOut.get()) {
			notifyAll();
			wait();
		}
		
		timerThread.interrupt();
		
		
		if (isRejected())
			throw new ExecutionException(error);
		else if (isFulfilled())
			return result;
		else
			return null;
	}
	
	public synchronized T await() throws InterruptedException, ExecutionException{
		while(isPending()) {
			notifyAll();
			wait();
		}
		
		if (isRejected())
			throw new ExecutionException(error);
		else
			return result;
	}
	
	public T await(Duration timeout) throws InterruptedException, ExecutionException{
		if (timeout.toMillis() < Long.MAX_VALUE / 1000)
			return await(timeout.toNanos(), TimeUnit.NANOSECONDS);
		else
			return await(timeout.toMillis(), TimeUnit.MILLISECONDS);
	}
	
	/** Promises cannot be cancelled. Will always return false. */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}
	
	/** Promises cannot be cancelled. Will always return false. */
	@Override
	public boolean isCancelled() {
		return false;
	}
	@Override
	public boolean isDone() {
		return isSettled();
	}
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return await();
	}
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return await(timeout, unit);
	}
	
	//factories:
	public static <T> Promise<T> asyncGet(Supplier<T> func){
		final var promise = new Promise<T>();
		final var thread = new Thread(() ->{
			try {
				promise.resolve(func.get());
			}
			catch(Throwable e) {
				promise.reject(e);
			}
		});
		thread.start();
		
		return promise;
	}
	
	public static Promise<Void> asyncRun(Runnable func){
		final var promise = new Promise<Void>();
		final var thread = new Thread(() -> {
			try {
				func.run();
				promise.resolve(null);
			}
			catch(Throwable e) {
				promise.reject(e);
			}
		});
		thread.start();
		
		return promise;
	}
	
	public static <T> Promise<T> threadInit(BiConsumer<Function<T, Boolean>, Function<Throwable, Boolean>> func){
		return new Promise<T>((resolve, reject) -> {
			final var thread = new Thread(() -> func.accept(resolve, reject));
			thread.start();
		});
	}
	
	public static <T> Promise<T> threadInit(Consumer<Function<T, Boolean>> func){
		return new Promise<T>((resolve) -> {
			final var thread = new Thread(() -> func.accept(resolve));
			thread.start();
		});
	}
	
	public static <T> Promise<T> resolved(T result){
		final var promise = new Promise<T>();
		promise.resolve(result);
		return promise;
	}
	
	public static <T> Promise<T> rejected(Throwable error){
		final var promise = new Promise<T>();
		promise.reject(error);
		return promise;
	}
	
	public static <T> Promise<T> fromFuture(Future<T> future){
		if (future instanceof Promise<T> p)
			return p;
		else if (future instanceof Task<T> t)
			return t.promise;
		else if (future instanceof Deferred<T> d)
			return d.promise;
		else if (future instanceof CompletableFuture<T> cf) {
			final var result = new Promise<T>();
			cf.thenAccept(t -> result.resolve(t));
			cf.exceptionally(e -> {result.reject(e); return null;});
			return result;
		}
		else {
			return Promise.asyncGet(() -> future.get());
		}
	}
	
	// cool static methods:
	/**
	 * @return A promise that resolves when all promises are fulfilled and
	 * rejects when any of the promises is rejected.
	 */
	public static Promise<Void> all(Iterable<Promise<?>> promises){
		final var iter = promises.iterator();
		final var count = new AtomicInteger(0);
		final var fulfilledCount = new AtomicInteger(0);
		
		final var result = new Promise<Void>();
		
		while (iter.hasNext() && !result.isRejected()) {
			count.incrementAndGet();
			final var promise = iter.next();
			
			promise.thenRun(() -> {
				fulfilledCount.incrementAndGet();
				if (!iter.hasNext() && fulfilledCount.get() == count.get()) {
					result.resolve(null);
				}
			});
			
			promise.onErrorAccept((e) -> {
				result.reject(e);
			});
		}
		
		if (fulfilledCount.get() == count.get())
			result.resolve(null);
		
		return result;
	}
	
	/**
	 * @return A promise that is settled with the outcome of the first promise
	 * to be settled (resolved or rejected). If resolved, the returned promise
	 * will resolve with the promise that resolved. If rejected, the returned 
	 * promise will reject with the error from the rejected promise.
	 */
	public static <T> Promise<Promise<T>> race(Iterable<Promise<T>> promises){
		final var result = new Promise<Promise<T>>();
		for(final var promise : promises) {
			if (result.isSettled())
				break;
			promise.thenRun(() -> result.resolve(promise));
			promise.onErrorAccept((e) -> result.reject(e));
		}
		
		return result;
	}
}

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
	Function<T, Promise<R>> then;
	Function<Throwable, Promise<R>> catcher;
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