package asynchronous.futures;

import asynchronous.futures.exceptions.PromiseCancellationException;
import functionPlus.Result;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;


/*
 * Total rip-off of javascript's promise, but what, this one has a cancel state to make things more complicated.
 */
public class Promise<T> implements Future<T> {
    private volatile T result = null;
    private volatile Throwable error = null;
    // flags for the 3 possible final states of the promise
    private volatile boolean fulfilled = false;
    private volatile boolean rejected = false;
    private volatile boolean cancelled = false;
    private final Object awaitLock = new Object();
    private final Queue<Callback<T, ?>> callbacks = new ConcurrentLinkedQueue<>();

    public Promise(Consumer<Settle> initializer) {
        initializer.accept(new Settle());
    }

    Promise() {
    }

    // ================ Properties =======================

    /**
     * Whether the Promise has been fulfilled, rejected, or cancelled
     */
    public boolean isSettled() {
        return fulfilled || rejected || cancelled;
    }

    /**
     * Whether the Promise is waiting to be settled
     */
    public boolean isPending() {
        return !isSettled();
    }

    /**
     * Whether the Promise has been fulfilled
     */
    public boolean isFulfilled() {
        return fulfilled;
    }

    /**
     * Whether the Promise has been rejected
     */
    public boolean isRejected() {
        return rejected;
    }

    /**
     * Whether the Promise has been cancelled
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @return The result of the Promise. Returns null if the Promise has yet
     * to be fulfilled (or if the result was actually null).
     */
    public T getResult() {
        return result;
    }

    /**
     * @return The error that the Promise was rejected with. Returns null if
     * the Promise has yet to be rejected (or if the error was actually of the
     * value null).
     */
    public Throwable getError() {
        return error;
    }

    // sideNote: every mutating method in this class is synchronized so that it doesn't break and stuff


    // handle's the event that the promise is resolved
    private synchronized void handleResolve(T result) {
        // apply state and result
        fulfilled = true;
        this.result = result;

        // call the callbacks with the new result
        resolveCallbacks(result);

        synchronized (awaitLock) {
            // notify any waiting threads (in the await method)
            awaitLock.notifyAll();
        }
    }

    // handle's the event that the promise is rejected
    private synchronized void handleReject(Throwable error) {
        // apply state and result
        rejected = true;
        this.error = error;

        // reject the callbacks with the new error
        rejectCallbacks(error);

        synchronized (awaitLock) {
            // notify any waiting threads (in the await method)
            awaitLock.notifyAll();
        }
    }

    // handle's the event that the promise is cancelled
    private synchronized void handleCancel() {
        //apply state
        cancelled = true;

        // cancel the callbacks
        cancelCallbacks();

        synchronized (awaitLock) {
            awaitLock.notifyAll();
        }
    }

    private boolean resolve(T result) {
        if (isSettled()) return false;

        synchronized (this) {
            if (isSettled()) {
                return false;
            } else {
                handleResolve(result);
                return true;
            }
        }
    }

    private boolean reject(Throwable error) {
        if (isSettled()) return false;

        synchronized (this) {
            if (isSettled()) {
                return false;
            } else {
                handleReject(error);
                return true;
            }
        }
    }

    private boolean cancel() {
        if (isSettled()) return false;

        synchronized (this) {
            if (isSettled()) {
                return false;
            } else {
                handleCancel();
                return true;
            }
        }
    }

    private boolean resolveFrom(Supplier<T> resultGetter) {
        if (isSettled()) return false;

        synchronized (this) {
            if (isSettled()) {
                return false;
            } else {
                try {
                    handleResolve(resultGetter.get());
                } catch (Throwable e) {
                    handleReject(e);
                }
                return true;
            }
        }
    }

    private boolean rejectFrom(Supplier<Throwable> errorGetter) {
        if (isSettled()) return false;

        synchronized (this) {
            if (isSettled()) {
                return false;
            } else {
                try {
                    handleReject(errorGetter.get());
                } catch (Throwable e) {
                    handleReject(e);
                }
                return true;
            }
        }
    }


    /**
     * Functional class. Use to settle the promise.
     */
    public class Settle {
        /**
         * Resolve the promise with the given result. Only takes effect if the
         * Promise hasn't been settled yet. If it has, the call will be ignored.
         *
         * @return Whether the call takes effect. If false: the call was ignored.
         */
        public boolean resolve(T result) {
            return Promise.this.resolve(result);
        }

        /**
         * Resolve the promise with null. Only takes effect if the
         * Promise hasn't been settled yet. If it has, the call will be ignored.
         *
         * @return Whether the call takes effect. If false: the call was ignored.
         */
        public boolean resolve() {
            return resolve(null);
        }

        /**
         * Reject the promise with the given error. Only takes effect if the
         * Promise hasn't been settled yet. If it has, the call will be ignored.
         *
         * @return Whether the call takes effect. If false: the call was ignored.
         */
        public boolean reject(Throwable error) {
            return Promise.this.reject(error);
        }

        /**
         * Cancel the promise. Only takes effect if the
         * Promise hasn't been settled yet. If it has, the call will be ignored.
         *
         * @return Whether the call takes effect. If false: the call was ignored.
         */
        public boolean cancel() {
            return Promise.this.cancel();
        }

        /**
         * If the promise hasn't been settled yet: runs the given function and
         * resolves the promise with the result. The function is not run if the
         * promise has already been settled.
         *
         * @param resultGetter Supplies the result to resolve the promise with.
         * @return Whether the function was run and it's result used.
         */
        public boolean resolveFrom(Supplier<T> resultGetter) {
            return Promise.this.resolveFrom(resultGetter);
        }

        /**
         * If the promise hasn't been settled yet: runs the given function and
         * rejects the promise with the result. The function is not run if the
         * promise has already been settled.
         *
         * @param errorGetter Supplies the error to reject the promise with.
         * @return Whether the function was run and it's result used.
         */
        public boolean rejectFrom(Supplier<Throwable> error) {
            return Promise.this.rejectFrom(error);
        }

        Settle() {
        }
    }

    // callback stuff

    /**
     * Resolve all callbacks with the given result
     */
    private synchronized void resolveCallbacks(T result) {
        Callback<T, ?> callback;
        while ((callback = callbacks.poll()) != null)
            callback.applyResolve(result);
    }

    /**
     * Reject all callbacks with the given error
     */
    private synchronized void rejectCallbacks(Throwable error) {
        Callback<T, ?> callback;
        while ((callback = callbacks.poll()) != null)
            callback.applyReject(error);
    }

    /**
     * Cancel all callbacks
     */
    private synchronized void cancelCallbacks() {
        Callback<T, ?> callback;
        while ((callback = callbacks.poll()) != null)
            callback.applyCancel();
    }

    private boolean trySettleCallbacks() {
        if (isFulfilled())
            resolveCallbacks(result);
        else if (isRejected())
            rejectCallbacks(error);
        else if (isCancelled())
            cancelCallbacks();
        else
            return false;

        return true;
    }

    /**
     * Adds the callback to the promise, all callback methods (then, onError, onSettled) end up calling this one.
     */
    private <R> Promise<R> addCallback(Callback<T, R> callback) {
        // puts the callback in the queue
        callbacks.add(callback);

        // try to settle the callbacks, this will settle the callback added if the promise has already been fulfilled.
        trySettleCallbacks();

        // return the callback's promise
        return callback.promise();
    }

    // then with error and cancel
    public <R> Promise<R> thenApply(Function<T, R> then, Function<Throwable, R> catcher, Supplier<R> onCancel) {
        return addCallback(new SyncCallback<R>(then, catcher, onCancel));
    }

    public Promise<T> thenAccept(Consumer<T> then, Consumer<Throwable> catcher, Runnable onCancel) {
        return addCallback(new SyncCallback<T>(t -> {
            then.accept(t);
            return t;
        }, e -> {
            catcher.accept(e);
            return null;
        }, () -> {
            onCancel.run();
            return null;
        }));
    }

    public <R> Promise<R> thenGet(Supplier<R> then, Supplier<R> catcher, Supplier<R> onCancel) {
        return addCallback(new SyncCallback<R>(t -> then.get(), e -> catcher.get(), onCancel));
    }

    public Promise<T> thenRun(Runnable then, Runnable catcher, Runnable onCancel) {
        return addCallback(new SyncCallback<T>(t -> {
            then.run();
            return result;
        }, e -> {
            catcher.run();
            return null;
        }, () -> {
            onCancel.run();
            return null;
        }));
    }

    // async then with error and cancel
    public <R> Promise<R> asyncThenApply(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher, Supplier<Promise<R>> onCancel) {
        return addCallback(new AsyncCallback<R>(then, catcher, onCancel));
    }

    public <R> Promise<R> asyncThenGet(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher, Supplier<Promise<R>> onCancel) {
        return addCallback(new AsyncCallback<R>(t -> then.get(), e -> catcher.get(), onCancel));
    }

    // then with error
    public <R> Promise<R> thenApply(Function<T, R> then, Function<Throwable, R> catcher) {
        return addCallback(new SyncCallback<R>(then, catcher, null));
    }

    public Promise<T> thenAccept(Consumer<T> then, Consumer<Throwable> catcher) {
        return addCallback(new SyncCallback<T>(t -> {
            then.accept(t);
            return t;
        }, e -> {
            catcher.accept(e);
            return null;
        }, null));
    }

    public <R> Promise<R> thenGet(Supplier<R> then, Supplier<R> catcher) {
        return addCallback(new SyncCallback<R>(t -> then.get(), e -> catcher.get(), null));
    }

    public Promise<T> thenRun(Runnable then, Runnable catcher) {
        return addCallback(new SyncCallback<T>(t -> {
            then.run();
            return t;
        }, e -> {
            catcher.run();
            return null;
        }, null));
    }

    // async then with error
    public <R> Promise<R> asyncThenApply(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher) {
        return addCallback(new AsyncCallback<R>(then, catcher, null));
    }

    public <R> Promise<R> asyncThenGet(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher) {
        return asyncThenApply(t -> then.get(), e -> catcher.get());
    }

    // just then
    public <R> Promise<R> thenApply(Function<T, R> then) {
        return addCallback(new SyncCallback<R>(then, null, null));
    }

    public Promise<T> thenAccept(Consumer<T> then) {
        return addCallback(new SyncCallback<T>(t -> {
            then.accept(t);
            return t;
        }, null, null));
    }

    public <R> Promise<R> thenGet(Supplier<R> then) {
        return addCallback(new SyncCallback<R>(t -> then.get(), null, null));
    }

    public Promise<T> thenRun(Runnable then) {
        return addCallback(new SyncCallback<T>(t -> {
            then.run();
            return t;
        }, null, null));
    }

    // async just then
    public <R> Promise<R> asyncThenApply(Function<T, Promise<R>> then) {
        return addCallback(new AsyncCallback<R>(then, null, null));
    }

    public <R> Promise<R> asyncThenGet(Supplier<Promise<R>> then) {
        return addCallback(new AsyncCallback<R>(t -> then.get(), null, null));
    }

    // on error
    public <R> Promise<R> onErrorApply(Function<Throwable, R> catcher) {
        return addCallback(new SyncCallback<R>(null, catcher, null));
    }

    public Promise<Void> onErrorAccept(Consumer<Throwable> catcher) {
        return addCallback(new SyncCallback<Void>(null, e -> {
            catcher.accept(e);
            return null;
        }, null));
    }

    public <R> Promise<R> onErrorGet(Supplier<R> catcher) {
        return addCallback(new SyncCallback<R>(null, e -> catcher.get(), null));
    }

    public Promise<Void> onErrorRun(Runnable catcher) {
        return addCallback(new SyncCallback<Void>(null, e -> {
            catcher.run();
            return null;
        }, null));
    }

    // async on error
    public <R> Promise<R> asyncOnErrorApply(Function<Throwable, Promise<R>> catcher) {
        return addCallback(new AsyncCallback<R>(null, catcher, null));
    }

    public <R> Promise<R> asyncOnErrorGet(Supplier<Promise<R>> catcher) {
        return addCallback(new AsyncCallback<R>(null, e -> catcher.get(), null));
    }

    // on cancel
    public <R> Promise<R> onCancelGet(Supplier<R> onCancel) {
        return addCallback(new SyncCallback<R>(null, null, onCancel));
    }

    public Promise<Void> onCancelRun(Runnable onCancel) {
        return addCallback(new SyncCallback<Void>(null, null, () -> {
            onCancel.run();
            return null;
        }));
    }

    // async on cancel
    public <R> Promise<R> asyncOnCancelGet(Supplier<Promise<R>> onCancel) {
        return addCallback(new AsyncCallback<R>(null, null, onCancel));
    }

    // on settled
    public <R> Promise<R> onSettledGet(Supplier<R> settler) {
        return addCallback(new SyncCallback<R>(t -> settler.get(), e -> settler.get(), settler));
    }


    public Promise<Void> onSettledRun(Runnable settler) {
        return addCallback(new SyncCallback<Void>(t -> {
            settler.run();
            return null;
        }, e -> {
            settler.run();
            return null;
        }, () -> {
            settler.run();
            return null;
        }));
    }

    // async on settled
    public <R> Promise<R> asyncOnSettledGet(Supplier<Promise<R>> settler) {
        return addCallback(new AsyncCallback<R>(t -> settler.get(), e -> settler.get(), settler));
    }

    // on unfulfilled
    public <R> Promise<R> onUnFulfilledGet(Supplier<R> onUnFulfilled) {
        return addCallback(new SyncCallback<R>(null, e -> onUnFulfilled.get(), onUnFulfilled));
    }

    public Promise<Void> onUnFulfilledRun(Runnable onUnFulfilled) {
        return addCallback(new SyncCallback<Void>(null, e -> {
            onUnFulfilled.run();
            return null;
        }, () -> {
            onUnFulfilled.run();
            return null;
        }));
    }

    // async on unfulfilled
    public <R> Promise<R> asyncOnUnFulfilledGet(Supplier<Promise<R>> onUnFulfilled) {
        return addCallback(new AsyncCallback<R>(null, e -> onUnFulfilled.get(), onUnFulfilled));
    }

    // on no error
    public <R> Promise<R> onNoErrorGet(Supplier<R> onNoError) {
        return addCallback(new SyncCallback<R>(r -> onNoError.get(), null, onNoError));
    }

    public Promise<Void> onNoErrorRun(Runnable onNoError) {
        return addCallback(new SyncCallback<Void>(r -> {
            onNoError.run();
            return null;
        }, null, () -> {
            onNoError.run();
            return null;
        }));
    }

    // async on no error
    public <R> Promise<R> asyncOnNoErrorGet(Supplier<Promise<R>> onNoError) {
        return addCallback(new AsyncCallback<R>(r -> onNoError.get(), null, () -> onNoError.get()));
    }

    // on no cancel
    public <R> Promise<R> onNoCancelGet(Supplier<R> onNoCancel) {
        return addCallback(new SyncCallback<R>(r -> onNoCancel.get(), e -> onNoCancel.get(), null));
    }

    public Promise<Void> onNoCancelRun(Runnable onNoCancel) {
        return addCallback(new SyncCallback<Void>(r -> {
            onNoCancel.run();
            return null;
        }, e -> {
            onNoCancel.run();
            return null;
        }, null));
    }

    // async on no cancel
    public <R> Promise<R> asyncOnNoCancelGet(Supplier<Promise<R>> onNoCancel) {
        return addCallback(new AsyncCallback<R>(r -> onNoCancel.get(), e -> onNoCancel.get(), null));
    }


    // auto-FunctionType versions:
    // then with error and cancel
    public <R> Promise<R> then(Function<T, R> then, Function<Throwable, R> catcher, Supplier<R> onCancel) {
        return thenApply(then, catcher, onCancel);
    }

    public Promise<T> then(Consumer<T> then, Consumer<Throwable> catcher, Runnable onCancel) {
        return thenAccept(then, catcher, onCancel);
    }

    public <R> Promise<R> then(Supplier<R> then, Supplier<R> catcher, Supplier<R> onCancel) {
        return thenGet(then, catcher, onCancel);
    }

    public Promise<T> then(Runnable then, Runnable catcher, Runnable onCancel) {
        return thenRun(then, catcher, onCancel);
    }

    public <R> Promise<R> asyncThen(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher, Supplier<Promise<R>> onCancel) {
        return asyncThenApply(then, catcher, onCancel);
    }

    public <R> Promise<R> asyncThen(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher, Supplier<Promise<R>> onCancel) {
        return asyncThenGet(then, catcher, onCancel);
    }

    // then with error
    public <R> Promise<R> then(Function<T, R> then, Function<Throwable, R> catcher) {
        return thenApply(then, catcher);
    }

    public Promise<T> then(Consumer<T> then, Consumer<Throwable> catcher) {
        return thenAccept(then, catcher);
    }

    public <R> Promise<R> then(Supplier<R> then, Supplier<R> catcher) {
        return thenGet(then, catcher);
    }

    public Promise<T> then(Runnable then, Runnable catcher) {
        return thenRun(then, catcher);
    }

    public <R> Promise<R> asyncThen(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> catcher) {
        return asyncThenApply(then, catcher);
    }

    public <R> Promise<R> asyncThen(Supplier<Promise<R>> then, Supplier<Promise<R>> catcher) {
        return asyncThenGet(then, catcher);
    }

    // then
    public <R> Promise<R> then(Function<T, R> then) {
        return thenApply(then);
    }

    public Promise<T> then(Consumer<T> then) {
        return thenAccept(then);
    }

    public <R> Promise<R> then(Supplier<R> then) {
        return thenGet(then);
    }

    public Promise<T> then(Runnable then) {
        return thenRun(then);
    }

    public <R> Promise<R> asyncThen(Function<T, Promise<R>> then) {
        return asyncThenApply(then);
    }

    public <R> Promise<R> AsyncThen(Supplier<Promise<R>> then) {
        return asyncThenGet(then);
    }

    // on error
    public <R> Promise<R> onError(Function<Throwable, R> catcher) {
        return onErrorApply(catcher);
    }

    public Promise<Void> onError(Consumer<Throwable> catcher) {
        return onErrorAccept(catcher);
    }

    public <R> Promise<R> onError(Supplier<R> catcher) {
        return onErrorGet(catcher);
    }

    public Promise<Void> onError(Runnable catcher) {
        return onErrorRun(catcher);
    }

    public <R> Promise<R> asyncOnError(Function<Throwable, Promise<R>> catcher) {
        return asyncOnErrorApply(catcher);
    }

    public <R> Promise<R> asyncOnError(Supplier<Promise<R>> catcher) {
        return asyncOnErrorGet(catcher);
    }

    // on cancel
    public <R> Promise<R> onCancel(Supplier<R> onCancel) {
        return onCancelGet(onCancel);
    }

    public Promise<Void> onCancel(Runnable onCancel) {
        return onCancelRun(onCancel);
    }

    public <R> Promise<R> asyncOnCancel(Supplier<Promise<R>> onCancel) {
        return asyncOnCancelGet(onCancel);
    }

    // on settled
    public <R> Promise<R> onSettled(Supplier<R> settler) {
        return onSettledGet(settler);
    }

    public Promise<Void> onSettled(Runnable settler) {
        return onSettledRun(settler);
    }

    public <R> Promise<R> asyncOnSettled(Supplier<Promise<R>> settler) {
        return asyncOnSettledGet(settler);
    }

    // on unfulfilled
    public <R> Promise<R> onUnFulfilled(Supplier<R> onUnFulfilled) {
        return onUnFulfilledGet(onUnFulfilled);
    }

    public Promise<Void> onUnFulfilled(Runnable onUnFulfilled) {
        return onUnFulfilledRun(onUnFulfilled);
    }

    public <R> Promise<R> asyncOnUnFulfilled(Supplier<Promise<R>> onUnFulfilled) {
        return asyncOnUnFulfilledGet(onUnFulfilled);
    }

    // on no error
    public <R> Promise<R> onNoError(Supplier<R> onNoError) {
        return onNoErrorGet(onNoError);
    }

    public Promise<Void> onNoError(Runnable onNoError) {
        return onNoErrorRun(onNoError);
    }

    public <R> Promise<R> asyncOnNoError(Supplier<Promise<R>> onNoError) {
        return asyncOnNoError(onNoError);
    }

    // on no cancel
    public <R> Promise<R> onNoCancel(Supplier<R> onNoCancel) {
        return onNoCancelGet(onNoCancel);
    }

    public Promise<Void> onNoCancel(Runnable onNoCancel) {
        return onNoCancelRun(onNoCancel);
    }

    public <R> Promise<R> asyncOnNoCancel(Supplier<Promise<R>> onNoCancel) {
        return asyncOnNoCancelGet(onNoCancel);
    }


    // blocking wait
    public T await(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
        if (isPending() && timeout > 0) {
            final var timedOut = new AtomicBoolean(false);

            final var timerThread = new Thread(() -> {
                try {
                    if (unit.toMillis(timeout) < Long.MAX_VALUE / 1000)
                        Thread.sleep(unit.toMillis(timeout), (int) (unit.toNanos(timeout) % 1000));
                    else
                        Thread.sleep(unit.toMillis(timeout));
                } catch (InterruptedException ie) {
                }

                timedOut.set(true);
                synchronized (awaitLock) {
                    awaitLock.notifyAll();
                }
            });


            timerThread.start();

            synchronized (awaitLock) {
                while (isPending() && !timedOut.get()) {
                    awaitLock.wait();
                }
            }

            timerThread.interrupt();
        }


        if (isRejected())
            throw new ExecutionException(error);
        else if (isCancelled())
            throw new PromiseCancellationException(this);
        else
            return result;
    }

    public T await() throws InterruptedException, ExecutionException {
        synchronized (awaitLock) {
            while (isPending()) {
                awaitLock.wait();
            }
        }

        if (isRejected())
            throw new ExecutionException(error);
        else if (isCancelled())
            throw new PromiseCancellationException(this);
        else
            return result;
    }

    public T await(Duration timeout) throws InterruptedException, ExecutionException {
        if (timeout.toMillis() < Long.MAX_VALUE / 1000)
            return await(timeout.toNanos(), TimeUnit.NANOSECONDS);
        else
            return await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public Result<T> awaitResult() throws InterruptedException, ExecutionException {
        synchronized (awaitLock) {
            while (isPending()) {
                awaitLock.wait();
            }
        }

        if (isFulfilled()) {
            return new Result<>(getResult());
        } else if (isRejected()) {
            throw new ExecutionException(getError());
        } else if (isCancelled()) {
            return new Result<>();
        } else {
            throw new IllegalStateException("Promise is no longer pending but is neither fulfilled, rejected, or cancelled.");
        }
    }

    /**
     * Promises cannot be cancelled directly. Will always return false.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    /** @return Whether the promise is settled. **/
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

    // ======================== Factory functions ========================
    public static class PromiseAndThread<T> {
        public final Promise<T> promise;
        public final Thread thread;

        private PromiseAndThread(Promise<T> promise, Thread thread) {
            this.promise = promise;
            this.thread = thread;
        }
    }

    /**
     * Runs the given function in a new Thread.
     *
     * @return Resolves when function completes (with the output of the function) and rejects if the function throws an error.
     */
    public static <T> PromiseAndThread<T> asyncGet(Supplier<T> func) {
        final var promise = new Promise<T>();
        final var thread = new Thread(() -> {
            try {
                promise.resolve(func.get());
            } catch (Throwable e) {
                promise.reject(e);
            }
        });
        thread.start();

        return new PromiseAndThread<T>(promise, thread);
    }

    /**
     * Runs the given function in a new Thread.
     *
     * @return Resolves when function completes and rejects if the function throws an error.
     */
    public static PromiseAndThread<Void> asyncRun(Runnable func) {
        final var promise = new Promise<Void>();
        final var thread = new Thread(() -> {
            try {
                func.run();
                promise.resolve(null);
            } catch (Throwable e) {
                promise.reject(e);
            }
        });
        thread.start();

        return new PromiseAndThread<Void>(promise, thread);
    }


    /**
     * Constructs a new Promise by running the initializer in a new thread.
     */
    public static <T> PromiseAndThread<T> threadInit(Consumer<Promise<T>.Settle> initializer) {
        final var promise = new Promise<T>();
        final var settle = promise.new Settle();
        final var thread = new Thread(() -> {
            try {
                initializer.accept(settle);
            } catch (Throwable e) {
                settle.reject(e);
            }
        });
        thread.start();

        return new PromiseAndThread<T>(promise, thread);
    }

    /**
     * Constructs a promise that is already resolved with the given result.
     */
    public static <T> Promise<T> resolved(T result) {
        final var promise = new Promise<T>();
        promise.resolve(result);
        return promise;
    }

    /**
     * Constructs a promise that is already rejected with the given error.
     */
    public static <T> Promise<T> rejected(Throwable error) {
        final var promise = new Promise<T>();
        promise.reject(error);
        return promise;
    }

    /**
     * Constructor a promise that is already cancelled.
     */
    public static <T> Promise<T> cancelled() {
        final var promise = new Promise<T>();
        promise.cancel();
        return promise;
    }

    public static class PromiseAndSettle<T> {
        public final Promise<T> promise;
        public final Promise<T>.Settle settle;

        public PromiseAndSettle(Promise<T> promise, Promise<T>.Settle settle) {
            this.promise = promise;
            this.settle = settle;
        }
    }

    /**
     * Returns a new promise and it's settle class.
     */
    public static <T> PromiseAndSettle<T> externalInit() {
        final var newPromise = new Promise<T>();
        return new PromiseAndSettle<T>(newPromise, newPromise.new Settle());
    }

    /**
     * Converts the given Future to a Promise.
     */
    public static <T> Promise<T> fromFuture(Future<T> future) {
        // first: the easy scenarios
        if (future == null)
            return null;
        else if (future instanceof Promise<T> p)
            return p;
        else if (future instanceof Task<T> t)
            return t.promise();
        else if (future instanceof Deferred<T> d)
            return d.promise();
        else if (future instanceof CompletableFuture<T> cf) {
            final var result = new Promise<T>();
            cf.thenAccept(t -> result.resolve(t));
            cf.exceptionally(e -> {
                if (cf.isCancelled())
                    result.cancel();
                else
                    result.reject(e);

                return null;
            });
            return result;
        }
        // second: the last resort:
        else {
            return Promise.<T>threadInit((settle) -> {
                try {
                    settle.resolve(future.get());
                } catch (Throwable e) {
                    if (future.isCancelled())
                        settle.cancel();
                    else
                        settle.reject(e);
                }
            }).promise;
        }
    }

    // cool static methods:

    /**
     * @return A promise that resolves when all promises are fulfilled and
     * rejects when any of the promises is rejected and cancels when any of the promises is cancelled.
     */
    public static Promise<Void> all(Iterable<Promise<?>> promises) {
        final var iter = promises.iterator();

        //How many promises there are so far
        final var count = new AtomicInteger(0);

        //How many promises have been fulfilled so far
        final var fulfilledCount = new AtomicInteger(0);

        final var result = new Promise<Void>();

        while (iter.hasNext() && !result.isRejected()) {
            // add the next promise to the count
            count.incrementAndGet();

            // process the next promise
            iter.next().thenApply(r -> {
                // add this fulfillment to the count
                fulfilledCount.incrementAndGet();

                // fulfill the result if the iterator has been fully processed. and every promise has been fulfilled.
                if (!iter.hasNext() && fulfilledCount.get() == count.get())
                    result.resolve(null);

                return null;
            }, e -> {
                result.reject(e);
                return null;
            }, () -> {
                result.cancel();
                return null;
            });
        }

        if (fulfilledCount.get() == count.get())
            result.resolve(null);

        return result;
    }

    /**
     * @return A promise that resolves when any of the promises is fulfilled or rejected. Cancels if all promises are cancelled.
     */
    public static <T> Promise<Promise<T>> any(Iterable<Promise<T>> promises) {
        final var result = new Promise<Promise<T>>();
        final var count = new AtomicInteger(0);
        final var cancelCount = new AtomicInteger(0);
        final var iter = promises.iterator();


        while (iter.hasNext() && result.isPending()) {
            count.incrementAndGet();
            final var promise = iter.next();

            promise.thenApply(r -> {
                result.resolve(promise);
                return null;
            }, e -> {
                result.reject(e);
                return null;
            }, () -> {
                cancelCount.incrementAndGet();
                if (!iter.hasNext() && cancelCount.get() == count.get()) {
                    result.cancel();
                }
                return null;
            });
        }

        if (cancelCount.get() == count.get()) {
            result.cancel();
        }

        return result;
    }

    // inner class "Callback" used for callback methods like then and onError
    private interface Callback<T, R> {
        void applyResolve(T result);

        void applyReject(Throwable error);

        void applyCancel();

        Promise<R> promise();
    }

    private class SyncCallback<R> implements Callback<T, R> {
        private final Function<T, R> then;
        private final Function<Throwable, R> onError;
        private final Supplier<R> onCancel;
        private final Promise<R> next = new Promise<R>();

        SyncCallback(Function<T, R> then, Function<Throwable, R> onError, Supplier<R> onCancel) {
            if (then != null)
                this.then = then;
            else
                this.then = t -> null;

            if (onError != null)
                this.onError = onError;
            else
                this.onError = e -> {
                    next.reject(e);
                    return null;
                };

            if (onCancel != null)
                this.onCancel = onCancel;
            else
                this.onCancel = () -> {
                    next.cancel();
                    return null;
                };
        }

        public Promise<R> promise() {
            return next;
        }

        @Override
        public void applyResolve(T result) {
            try {
                next.resolve(then.apply(result));
            } catch (Throwable e) {
                next.reject(e);
            }
        }

        @Override
        public void applyReject(Throwable error) {
            try {
                next.resolve(onError.apply(error));
            } catch (Throwable e) {
                next.reject(e);
            }
        }

        @Override
        public void applyCancel() {
            try {
                next.resolve(onCancel.get());
            } catch (Throwable e) {
                next.reject(e);
            }
        }
    }

    private class AsyncCallback<R> implements Callback<T, R> {
        private final Function<T, Promise<R>> then;
        private final Function<Throwable, Promise<R>> onError;
        private final Supplier<Promise<R>> onCancel;
        private final Promise<R> next = new Promise<R>();
        private volatile boolean applied = false;

        AsyncCallback(Function<T, Promise<R>> then, Function<Throwable, Promise<R>> onError, Supplier<Promise<R>> onCancel) {
            if (then != null)
                this.then = then;
            else
                this.then = t -> Promise.<R>resolved(null);

            if (onError != null)
                this.onError = onError;
            else
                this.onError = e -> {
                    next.reject(e);
                    return null;
                };

            if (onCancel != null)
                this.onCancel = onCancel;
            else
                this.onCancel = () -> {
                    next.cancel();
                    return null;
                };
        }

        public Promise<R> promise() {
            return next;
        }

        @Override
        public void applyResolve(T result) {
            if (applied) return;
            synchronized (next) {
                if (applied || next.isSettled())
                    return;
                then.apply(result).thenAccept(
                        r -> next.resolve(r),
                        e -> next.reject(e),
                        () -> next.cancel());
                applied = true;
                return;
            }
        }

        @Override
        public void applyReject(Throwable error) {
            if (applied) return;
            synchronized (next) {
                if (applied || next.isSettled())
                    return;
                onError.apply(error).thenAccept(
                        r -> next.resolve(r),
                        e -> next.reject(e),
                        () -> next.cancel());
                applied = true;
                return;
            }
        }

        @Override
        public void applyCancel() {
            if (applied) return;
            synchronized (next) {
                if (applied || next.isSettled())
                    return;

                onCancel.get().thenAccept(
                        r -> next.resolve(r),
                        e -> next.reject(e),
                        () -> next.cancel());
                applied = true;
                return;
            }
        }
    }
}

