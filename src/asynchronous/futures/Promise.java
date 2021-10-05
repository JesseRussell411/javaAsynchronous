package asynchronous.futures;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;


public class Promise<T> implements Future<T>{
	private volatile T result = null;
	private volatile Throwable error = null;
	private volatile boolean fulfilled;
	private volatile boolean rejected;
	private final Object awaitLock = new Object();
	private final List<Callback<T, ?>> callbacks = new ArrayList<Callback<T, ?>>();
	
	/** Whether the Promise is waiting to be settled */
	public boolean isPending() { return !fulfilled && !rejected; }
	/** Whether the Promise has been fulfilled or rejected */
	public boolean isSettled() { return fulfilled || rejected; }
	/** Whether the Promise has been fulfilled */
	public boolean isFulfilled() { return fulfilled; }
	/** Whether the Promise has been rejected */
	public boolean isRejected() { return rejected; }
	
	/** @return The result of the Promise. Returns null if the Promise has yet
	 * to be fulfilled (or if the result was actually null). */
	public T getResult() { return result; }
	
	/** @return The error that the Promise was rejected with. Returns null if
	 * the Promise has yet to be rejected (or if the error was actually of the
	 * value null). */
	public Throwable getError() { return error; }
	
	// every mutating method in this class is synchronized so that it doesn't break and stuff
	
	
	private synchronized void handleResolve(T result) {
		// apply state and result
		fulfilled = true;
		this.result = result;
		
		// call the callbacks with the new result
		resolveCallbacks(result);
			
		synchronized(awaitLock) {
			// notify any waiting threads (in the await method)
			awaitLock.notifyAll();
		}
	}
	
	private synchronized void handleReject(Throwable error) {
		// apply state and result
		rejected = true;
		this.error = error;
		
		// reject the callbacks with the new error
		rejectCallbacks(error);
		
		synchronized(awaitLock) {
			// notify any waiting threads (in the await method)
			awaitLock.notifyAll();
		}
	}
	
	/** 
	 * Resolve the promise with the given result, only takes effect if the
	 * Promise hasn't been settled yet. If it has, the call will be ignored.
	 * @return Whether the call takes effect. If false: the call was ignored.
	 * */
	synchronized boolean resolve(T result) {
		if (isSettled()) {
			return false;
		}
		else {
			handleResolve(result);
			return true;
		}
	}
	
	/** 
	 * Reject the promise with the given error, only takes effect if the
	 * Promise hasn't been settled yet. If it has, the call will be ignored.
	 * @return Whether the call takes effect. If false: the call was ignored.
	 * */
	synchronized boolean reject(Throwable error) {
		if (isSettled()) {
			return false;
		}
		else {
			handleReject(error);
			return true;
		}
	}
	
	synchronized boolean resolveFrom(Supplier<T> resultGetter) {
		if (isSettled()) {
			return false;
		}
		else {
			try {
				handleResolve(resultGetter.get());
			}
			catch(Throwable e) {
				handleReject(e);
			}
			return true;
		}
	}
	
	synchronized boolean rejectFrom(Supplier<Throwable> errorGetter) {
		if (isSettled()) {
			return false;
		} else {
			try {
				handleReject(errorGetter.get());
			}
			catch(Throwable e) {
				handleReject(e);
			}
			return true;
		}
	}
	
	public class Settle {
		public boolean resolve(T result) {
			return Promise.this.resolve(result);
		}
		public boolean reject(Throwable error) {
			return Promise.this.reject(error);
		}
		public boolean resolve() { return resolve(null); }
		public boolean resolveFrom(Supplier<T> resultGetter) {
			return Promise.this.resolveFrom(resultGetter);
		}
		public boolean rejectFrom(Supplier<Throwable> error) {
			return Promise.this.rejectFrom(error);
		}
		
		Settle(){}
	}
	
	Promise(){}
	
	public Promise(Consumer<Settle> initializer) {
		initializer.accept(new Settle());
	}
	
	// callback stuff
	/** Resolve all callbacks with the given result */
	private synchronized void resolveCallbacks(T result) {
		for(final var callback : callbacks)
			callback.applyResolve(result);
		callbacks.clear();
	}
	/** Reject all callbacks with the given error */
	private synchronized void rejectCallbacks(Throwable error) {
		for(final var callback : callbacks)
			callback.applyReject(error);
		callbacks.clear();
	}
	
	/** Adds the callback to the promise, all callback methods (then, onError, onSettled) end up calling this one. */
	private synchronized <R> Promise<R> addCallback(Callback<T, R> callback){
		// if the promise has already been settled: run the callback now
		// instead of putting it in the queue
		if (isFulfilled())
			callback.applyResolve(result);
		else if (isRejected())
			callback.applyReject(error);
		else
			callbacks.add(callback);
		
		// return the callback's promise
		return callback.getNext();
	}
	
	// then and error
	public synchronized <R> Promise<R> thenApply(Function<T, R> then, Function<Throwable, R> catcher){
		return addCallback(new SyncCallback<T, R>(then, catcher));
	}
	
	public synchronized Promise<T> thenAccept(Consumer<T> then, Consumer<Throwable> catcher){
		return thenApply(t -> {
			then.accept(t);
			return t;
		}, e -> {
			catcher.accept(e);
			return null;
		});
	}
	
	public synchronized <R> Promise<R> thenGet(Supplier<R> then, Supplier<R> catcher){
		return thenApply(t -> then.get(), e -> catcher.get());
	}
	
	public synchronized Promise<T> thenRun(Runnable then, Runnable catcher){
		return thenApply(t -> {
			then.run();
			return t;
		}, e -> {
			catcher.run();
			return null;
		});
	}
	
	
	public synchronized <R> Promise<R> asyncThenApply(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher){
		return addCallback(new AsyncCallback<T, R>(then, catcher));
	}
	
	public synchronized <R> Promise<R> asyncThenGet(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher){
		return asyncThenApply(t -> then.get(), e -> catcher.get());
	}
	
	// just then
	public synchronized <R> Promise<R> thenApply(Function<T, R> then){
		return thenApply(then, null);
	}
	
	public synchronized Promise<T> thenAccept(Consumer<T> then){
		return thenApply(t -> {
			then.accept(t);
			return t;
		});
	}
	
	public synchronized <R> Promise<R> thenGet(Supplier<R> then){
		return thenApply(r -> then.get());
	}
	
	public synchronized Promise<T> thenRun(Runnable then){
		return thenApply(t -> {
			then.run();
			return t;
		});
	}
	
	public synchronized <R> Promise<R> asyncThenApply(Function<T, Promise<R>> then){
		return addCallback(new AsyncCallback<T, R>(then, null));
	}
	
	public synchronized <R> Promise<R> asyncThenGet(Supplier<Promise<R>> then){
		return asyncThenApply(t -> then.get());
	}
	
	// on error
	public synchronized <R> Promise<R> onErrorApply(Function<Throwable, R> catcher){
		return thenApply(null, catcher);
	}
	
	public synchronized Promise<T> onErrorAccept(Consumer<Throwable> catcher) {
		return thenApply(t -> t, e -> {
			catcher.accept(e);
			return null;
		});
	}
	
	public synchronized <R> Promise<R> onErrorGet(Supplier<R> catcher){
		return onErrorApply(e -> catcher.get());
	}
	
	public synchronized Promise<T> onErrorRun(Runnable catcher){
		return thenApply(t -> t, e -> {
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
	
	public synchronized Promise<T> onSettledRun(Runnable settler){
		return thenApply(t -> {
			settler.run();
			return t;
		}, e -> {
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
	public synchronized Promise<T>     then(Consumer<T> then, Consumer<Throwable> catcher){return thenAccept(then, catcher);}
	public synchronized <R> Promise<R> then(Supplier<R> then, Supplier<R> catcher){return thenGet(then, catcher);}
	public synchronized Promise<T>     then(Runnable then, Runnable catcher){return thenRun(then, catcher);}
	public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher){return asyncThenApply(then, catcher);}
	public synchronized <R> Promise<R> asyncThen(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher){return asyncThenGet(then, catcher);}
	
	// just then
	public synchronized <R> Promise<R> then(Function<T, R> then){return thenApply(then);}
	public synchronized Promise<T>     then(Consumer<T> then){return thenAccept(then);}
	public synchronized <R> Promise<R> then(Supplier<R> then){return thenGet(then);}
	public synchronized Promise<T>     then(Runnable then){return thenRun(then);}
	public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> then){return asyncThenApply(then);}
	public synchronized <R> Promise<R> AsyncThen(Supplier<Promise<R>> then){return asyncThenGet(then);}
	
	// on error
	public synchronized <R> Promise<R> onError(Function<Throwable, R> catcher){return onErrorApply(catcher);}
	public synchronized Promise<T>     onError(Consumer<Throwable> catcher) {return onErrorAccept(catcher);}
	public synchronized <R> Promise<R> onError(Supplier<R> catcher){return onErrorGet(catcher);}
	public synchronized Promise<T>     onError(Runnable catcher){return onErrorRun(catcher);}
	public synchronized <R> Promise<R> asyncOnError(Function<Throwable, Promise<R>> catcher){return asyncOnErrorApply(catcher);}
	public synchronized <R> Promise<R> asyncOnError(Supplier<Promise<R>> catcher){return asyncOnErrorGet(catcher);}
	
	// on settled
	public synchronized <R> Promise<R> onSettled(Supplier<R> settler){return onSettledGet(settler);}
	public synchronized Promise<T>     onSettled(Runnable settler){return onSettledRun(settler);}
	public synchronized <R> Promise<R> asyncOnSettled(Supplier<Promise<R>> settler){return asyncOnSettledGet(settler);}
	
	
	// blocking wait
	public T await(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException{
		if (isPending() && timeout > 0) {
			final var timedOut = new AtomicBoolean(false);
			
			final var timerThread = new Thread(() -> {
				try {
					if (unit.toMillis(timeout) < Long.MAX_VALUE / 1000)
						Thread.sleep(unit.toMillis(timeout), (int)(unit.toNanos(timeout) % 1000));
					else
						Thread.sleep(unit.toMillis(timeout));
				}
				catch(InterruptedException ie) {}
				
				timedOut.set(true);
				synchronized(awaitLock) {
					awaitLock.notifyAll();
				}
			});
			
			
			timerThread.start();
			
			synchronized(awaitLock) {
				while(isPending() && !timedOut.get()) {
					awaitLock.wait();
				}
			}
			
			timerThread.interrupt();
		}
		
		
		if (isRejected())
			throw new ExecutionException(error);
		else if (isFulfilled())
			return result;
		else
			return null;
	}
	
	public T await() throws InterruptedException, ExecutionException{
		synchronized(awaitLock) {
			while(isPending()) {
				awaitLock.wait();
			}
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
	/**
	 * Runs the given function in a new Thread.
	 * @return Resolves when function completes (with the output of the function) and rejects if the function throws an error.
	 */
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
	
	/**
	 * Runs the given function in a new Thread.
	 * @return Resolves when function completes and rejects if the function throws an error.
	 */
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
	
	
	public static class threadInit_result<T>{
		public final Promise<T> promise;
		public final Thread thread;
		private threadInit_result(Promise<T> promise, Thread thread){
			this.promise = promise;
			this.thread = thread;
		}
	}
	/**
	 * Constructs a new Promise by running the initializer in parallel.
	 */
	public static <T> threadInit_result<T> threadInit(Consumer<Promise<T>.Settle> initializer){
		final var promise = new Promise<T>();
		final var settle = promise.new Settle();
		final var thread = new Thread(() -> {
			try {
				initializer.accept(settle);
			}
			catch(Throwable e) {
				settle.reject(e);
			}
		});
		thread.start();
		
		return new threadInit_result<T>(promise, thread);
	}
	
	/**
	 * Constructs a promise that is already resolved with the given result.
	 */
	public static <T> Promise<T> resolved(T result){
		final var promise = new Promise<T>();
		promise.resolve(result);
		return promise;
	}
	
	/**
	 * Constructs a promise that is already rejected with the given error.
	 */
	public static <T> Promise<T> rejected(Throwable error){
		final var promise = new Promise<T>();
		promise.reject(error);
		return promise;
	}
	
	/**
	 * Converts the given Future to a Promise.
	 */
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
			return Promise.<T>threadInit((settle) -> {
				try {
					settle.resolve(future.get());
				}
				catch(Throwable e) {
					settle.reject(e);
				}
			}).promise;
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
				
				if (!iter.hasNext() && fulfilledCount.get() == count.get())
					result.resolve(null);
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

class SyncCallback<T, R> implements Callback<T, R> {
	private final Function<T, R> then;
	private final Function<Throwable, R> catcher;
	private final Promise<R> next = new Promise<R>();
	
	SyncCallback(Function<T, R> then, Function<Throwable, R> catcher) {
		if (then != null)
			this.then = then;
		else
			this.then = t -> null;
			
		if (catcher != null)
			this.catcher = catcher;
		else
			this.catcher = e -> {next.reject(e); return null;};
	}
	
	public Promise<R> getNext() { return next; }
	
	@Override
	public boolean applyResolve(T result) {
		synchronized(next) {
			try {
				return next.resolve(then.apply(result));
			}
			catch(Throwable e) {
				return next.reject(e);
			}
		}
	}
	
	@Override
	public boolean applyReject(Throwable error){
		synchronized(next) {
			try {
				return next.resolve(catcher.apply(error));
			}
			catch(Throwable e) {
				return next.reject(e);
			}
		}
	}
}

class AsyncCallback<T, R> implements Callback<T, R>{
	private final Function<T, Promise<R>> then;
	private final Function<Throwable, Promise<R>> catcher;
	private final Promise<R> next = new Promise<R>();
	private volatile boolean applied = false;
	
	AsyncCallback(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher) {
		if (then != null)
			this.then = then;
		else
			this.then = t -> Promise.<R>resolved(null);
			
		if (catcher != null)
			this.catcher = catcher;
		else
			this.catcher = e -> {next.reject(e); return Promise.<R>resolved(null);};
	}
	
	public Promise<R> getNext() { return next; }
	
	@Override
	public boolean applyResolve(T result) {
		synchronized(next) {
			if (applied || next.isSettled()) {
				return false;
			}
			else {
				then.apply(result).thenAccept(r -> next.resolve(r), e -> next.reject(e));
				applied = true;
				return true;
			}
		}
	}
	
	@Override
	public boolean applyReject(Throwable error){
		synchronized(next) {
			if (applied || next.isSettled()) {
				return false;
			}
			else {
				catcher.apply(error).thenAccept(r -> next.resolve(r), e -> next.reject(e));
				applied = true;
				return true;
			}
		}
	}
}