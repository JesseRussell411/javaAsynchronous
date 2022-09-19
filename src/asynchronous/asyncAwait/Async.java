package asynchronous.asyncAwait;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

import asynchronous.CoThread;
import asynchronous.*;
import asynchronous.futures.Deferred;
import asynchronous.futures.Promise;
import asynchronous.futures.exceptions.FutureCancellationException;
import atom.Atom;
import exceptionsPlus.UncheckedWrapper;
import functionPlus.*;


/**
 * Asynchronous function used for asynchronous programming. Call Async.execute at the end of the main method to run called Async functions.
 *
 * @author jesse
 */
public class Async {
    private final AtomicInteger runningInstanceCount = new AtomicInteger(0);
    private final Queue<AsyncSupplier<?>.CalledInstance> executionQueue = new ConcurrentLinkedQueue<>();
    private final Object executeWaitLock = new Object();

    /**
     * Notify Async class that the instance has started
     */
    private void asyncStartNotify(AsyncSupplier<?>.CalledInstance inst) {
        synchronized (executeWaitLock) {
            executionQueue.add(inst);
            runningInstanceCount.incrementAndGet();
            executeWaitLock.notify();
        }
    }

    /**
     * Notify Async class that an awaited promise has completed
     *
     * @param inst The instance awaiting the promise
     */
    private void asyncAwaitCompleteNotify(AsyncSupplier<?>.CalledInstance inst) {
        synchronized (executeWaitLock) {
            executionQueue.add(inst);
            executeWaitLock.notify();
        }
    }

    /**
     * Notify Async class that an instance has completed
     */
    private void asyncCompleteNotify() {
        synchronized (executeWaitLock) {
            runningInstanceCount.decrementAndGet();
            executeWaitLock.notify();
        }
    }

    /**
     * Runs all Async instances in the execution queue.
     *
     * @throws InterruptedException
     */
    public void execute(Atom<Integer> maxThreadCount, Atom<Boolean> listen, Atom<Boolean> stop) throws InterruptedException {
        try (final var maxThreadCountObserver = maxThreadCount.tempObserve(); final var listenObserver = listen.tempObserve(); final var stopObserver = stop.tempObserve()) {

            maxThreadCountObserver.react(change -> {
                synchronized (executeWaitLock) {
                    executeWaitLock.notifyAll();
                }
            });
            stopObserver.react(change -> {
                if (change.newValue == false)
                    return;

                synchronized (executeWaitLock) {
                    executeWaitLock.notifyAll();
                }
            });
            listenObserver.react(change -> {
                synchronized (executeWaitLock) {
                    executeWaitLock.notifyAll();
                }
            });

            final var threadCount = new AtomicInteger();

            // execution loop
            do {
                AsyncSupplier<?>.CalledInstance instance;
                while (!stop.get() && threadCount.get() < maxThreadCount.get() && (instance = executionQueue.poll()) != null) {
                    threadCount.incrementAndGet();

                    // execute the instance
                    // the returned promise will tell us when the instance yields again or if it completes or throws an error.
                    instance.execute().onSettledRun(() -> {
                        threadCount.decrementAndGet();
                        synchronized (executeWaitLock) {
                            executeWaitLock.notifyAll();
                        }
                    });
                }

                synchronized (executeWaitLock) {
                    executeWaitLock.notifyAll();
                    while (!stop.get()) {
                        // if the max thread count is zero, pause.
                        if (maxThreadCount.get() != 0) {
                            // exit conditions
                            if (!listen.get() && executionQueue.isEmpty() && runningInstanceCount.get() == 0)
                                break;
                            // resume conditions
                            if (!executionQueue.isEmpty()) break;
                        }

                        executeWaitLock.wait();
                    }
                }
            } while (!stop.get() && !(!listen.get() && executionQueue.isEmpty() && runningInstanceCount.get() == 0));

        }
    }

    public void execute(Atom<Boolean> listen, Atom<Boolean> stop) throws InterruptedException {
        execute(new Atom<Integer>(1), listen, stop);
    }

    public void execute(Atom<Integer> maxThreadCount) throws InterruptedException {
        execute(maxThreadCount, new Atom<Boolean>(false), new Atom<Boolean>(false));
    }

    public void execute(int maxThreadCount, boolean listen) throws InterruptedException {
        execute(new Atom<Integer>(maxThreadCount), new Atom<Boolean>(listen), new Atom<Boolean>(false));
    }

    public void execute(int maxThreadCount) throws InterruptedException {
        execute(new Atom<Integer>(maxThreadCount), new Atom<Boolean>(false), new Atom<Boolean>(false));
    }

    public void execute(int maxThreadCount, boolean listen, Atom<Boolean> stop) throws InterruptedException {
        execute(new Atom<Integer>(maxThreadCount), new Atom<Boolean>(listen), stop);
    }

    public void execute(int maxThreadCount, Atom<Boolean> stop) throws InterruptedException {
        execute(new Atom<Integer>(maxThreadCount), new Atom<Boolean>(false), stop);
    }

    public void execute() throws InterruptedException {
        execute(new Atom<Integer>(1), new Atom<Boolean>(false), new Atom<Boolean>(false));
    }


    // Await functional class for awaiting futures in an Async functional class.
    public class Await {
        private final CoThread<Promise<?>>.Yield yields;

        // can't be instantiated by the user. Only Async and itself (but only Async should)
        private Await(CoThread<Promise<?>>.Yield yields) {
            this.yields = yields;
        }

        /**
         * Awaits the given future, returning its result when it's resolved.
         *
         * @param <T>    The type of the future.
         * @param future The future to await.
         * @return A Result representing the result of future or null if the future is null. The Result is undefined if
         * the future was canceled, or defined with the result of the future if it was not.
         * @throws UncheckedWrapper Wrapper around all Exceptions checked and un-checked. Will contain whatever
         *                          exception was thrown.
         */
        public <T> Result<T> getResult(Future<T> future) throws UncheckedWrapper {
            if (future == null) {
                return new Result<>(null);
            }

            final var promise = Promise.fromFuture(future);

            try {
                // yields to Async.execute. wait for the promise to complete. Async.execute will take care of that.
                yields.accept(promise);

                // at this point yields has stopped blocking which should mean that the promise is complete.
                if (promise.isSettled()) {
                    if (promise.isFulfilled()) {
                        return new Result<>(promise.getResult());
                    } else if (promise.isRejected()) {
                        throw promise.getError();
                    } else if (promise.isCancelled()) {
                        return new Result<>();
                    } else {
                        throw new IllegalStateException("The promise given to Async.Await.apply has been settled but is not fulfilled, rejected, or cancelled.");
                    }
                } else {
                    throw new IllegalStateException("The promise given to Async.Await.apply has not been settled after yielding.");
                }
            } catch (Throwable e) {
                throw UncheckedWrapper.uncheckify(e);
            }
        }

        /**
         * Awaits the given future, returning its result when it's resolved.
         *
         * @param <T>      The type of the future.
         * @param future   The future to await.
         * @param onCancel called if the future is cancelled. if null: throws a PromiseCancellationException if the future is cancelled.
         * @return result of future or null if the future is null.
         * @throws UncheckedWrapper Wrapper around all Exceptions checked and un-checked. Will contain whatever exception was thrown.
         *                          This is the only exception thrown by await.apply. PromiseCancellationException: if the future is cancelled and onCancel was null.
         */
        public <T> T apply(Future<T> future, Supplier<T> onCancel) throws UncheckedWrapper, FutureCancellationException {
            final var result = getResult(future);
            if (result.undefined) {
                if (onCancel != null)
                    return onCancel.get();
                else
                    throw new FutureCancellationException(future);
            } else {
                return result.value;
            }
        }

        public <T> T apply(Future<T> future) throws UncheckedWrapper {
            return apply(future, null);
        }

        /**
         * Asynchronously waits for the given function to run in a separate thread.
         *
         * @return whatever was returned by the function;
         */
        public <T> T func(Supplier<T> func) {
            return apply(Promise.asyncGet(func).promise);
        }

        /**
         * Asynchronously waits for the given function to run in a separate thread.
         */
        public void func(Runnable func) {
            apply(Promise.asyncRun(func).promise);
        }

        // utils:

        /**
         * Asynchronous sleep function. May sleep for longer than the specified time while the instance waits its turn to execute again.
         */
        public void sleep(long milliseconds, int nanoseconds) {
            apply(Timing.setTimeout(() -> null, milliseconds, nanoseconds));
        }

        /**
         * Asynchronous sleep function. May sleep for longer than the specified time while the instance waits its turn to execute again.
         */
        public void sleep(long milliseconds) {
            apply(Timing.setTimeout(() -> null, milliseconds));
        }
    }


// o-------------------o
// | function classes: |
// o-------------------o

    /**
     * Asynchronous function used for asynchronous programming. Call Async.execute at the end of the main method to run called Async functions.
     *
     * @param <T>
     * @author jesse
     */
    public class AsyncSupplier<T> implements Supplier<Promise<T>> {
        private final Function<Await, T> func;
        private final String name;

        public String getName() {
            return name;
        }

        public AsyncSupplier(Function<Await, T> func) {
            this(func, null);
        }

        public AsyncSupplier(Function<Await, T> func, String name) {
            this.func = func;
            if (name == null)
                this.name = "async";
            else
                this.name = name;
        }

        public Promise<T> get() {
            var inst = new CalledInstance();
            return inst.start();
        }


        /**
         * Call to an Async function.
         *
         * @author jesse
         */
        private class CalledInstance {
            private final CoThread<Promise<?>> coThread;
            private volatile T result = null;
            private volatile Deferred<T> deferred;

            CalledInstance() {
                coThread = new CoThread<>(yields -> {
                    result = func.apply(new Await(yields));
                }, name);
            }

            private synchronized Promise<T> start() {
                // make a new promise and extract resolve and reject methods
                deferred = new Deferred<T>();

                // add callback to promise that decrements running instance count when the call completes.
                deferred.promise().onSettledRun(() -> asyncCompleteNotify());

                // Notify Async class that this instance has started.
                asyncStartNotify(this);

                // This promise will resolve when the instance completes successfully, and reject when an error occurs
                return deferred.promise();
            }

            private synchronized Promise<Promise<?>> execute() {
                return coThread.run().thenAccept(result -> {
                    result.onSettledRun(() ->
                            asyncAwaitCompleteNotify(this));
                }, error -> {
                    deferred.settle().reject(error);
                }, () -> {
                    coThread.close();
                    deferred.settle().resolve(this.result);
                });
            }
        }
    }

    public class AsyncRunnable implements Supplier<Promise<Void>> {
        private final AsyncSupplier<Void> async;

        public AsyncRunnable(Consumer<Await> func, String name) {
            async = new AsyncSupplier<Void>(
                    await -> {
                        func.accept(await);
                        return null;
                    }, name);
        }

        public AsyncRunnable(Consumer<Await> func) {
            this(func, null);
        }

        public synchronized Promise<Void> get() {
            return async.get();
        }

        public String getName() {
            return async.getName();
        }
    }

    private class AsyncNargsFunction<R> {
        private final AsyncSupplier<R> async;
        private Object[] args = null;
        private final Lock argsLock = new ReentrantLock();

        public AsyncNargsFunction(BiFunction<Await, Object[], R> func, String name) {
            async = new AsyncSupplier<>((await) -> {
                final var args_localCopy = args;
                argsLock.unlock();

                return func.apply(await, args_localCopy);
            }, name);
        }

        public Promise<R> apply(Object[] args) {
            argsLock.lock();
            this.args = args;
            return async.get();
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncFunction<T1, R> implements Function<T1, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncFunction(BiFunction<Await, T1, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0]), name);
        }

        public AsyncFunction(BiFunction<Await, T1, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1) {
            return async.apply(new Object[]{t1});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncConsumer<T1> implements Function<T1, Promise<Void>> {
        private final AsyncFunction<T1, Void> async;

        public AsyncConsumer(BiConsumer<Await, T1> func, String name) {
            async = new AsyncFunction<>((await, t1) -> {
                func.accept(await, t1);
                return null;
            }, name);
        }

        public AsyncConsumer(BiConsumer<Await, T1> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1) {
            return async.apply(t1);
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncBiFunction<T1, T2, R> implements BiFunction<T1, T2, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncBiFunction(TriFunction<Await, T1, T2, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0], (T2) args[1]), name);
        }

        public AsyncBiFunction(TriFunction<Await, T1, T2, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1, T2 t2) {
            return async.apply(new Object[]{t1, t2});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncBiConsumer<T1, T2> implements BiFunction<T1, T2, Promise<Void>> {
        private final AsyncBiFunction<T1, T2, Void> async;

        public AsyncBiConsumer(TriConsumer<Await, T1, T2> func, String name) {
            async = new AsyncBiFunction<>((await, t1, t2) -> {
                func.accept(await, t1, t2);
                return null;
            }, name);
        }

        public AsyncBiConsumer(TriConsumer<Await, T1, T2> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1, T2 t2) {
            return async.apply(t1, t2);
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncTriFunction<T1, T2, T3, R> implements TriFunction<T1, T2, T3, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncTriFunction(QuadFunction<Await, T1, T2, T3, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0], (T2) args[1], (T3) args[2]), name);
        }

        public AsyncTriFunction(QuadFunction<Await, T1, T2, T3, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1, T2 t2, T3 t3) {
            return async.apply(new Object[]{t1, t2, t3});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncTriConsumer<T1, T2, T3> implements TriFunction<T1, T2, T3, Promise<Void>> {
        private final AsyncTriFunction<T1, T2, T3, Void> async;

        public AsyncTriConsumer(QuadConsumer<Await, T1, T2, T3> func, String name) {
            async = new AsyncTriFunction<>((await, t1, t2, t3) -> {
                func.accept(await, t1, t2, t3);
                return null;
            }, name);
        }

        public AsyncTriConsumer(QuadConsumer<Await, T1, T2, T3> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1, T2 t2, T3 t3) {
            return async.apply(t1, t2, t3);
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncQuadFunction<T1, T2, T3, T4, R> implements QuadFunction<T1, T2, T3, T4, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncQuadFunction(PentaFunction<Await, T1, T2, T3, T4, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0], (T2) args[1], (T3) args[2], (T4) args[3]), name);
        }

        public AsyncQuadFunction(PentaFunction<Await, T1, T2, T3, T4, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4) {
            return async.apply(new Object[]{t1, t2, t3, t4});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncQuadConsumer<T1, T2, T3, T4> implements QuadFunction<T1, T2, T3, T4, Promise<Void>> {
        private final AsyncQuadFunction<T1, T2, T3, T4, Void> async;

        public AsyncQuadConsumer(PentaConsumer<Await, T1, T2, T3, T4> func, String name) {
            async = new AsyncQuadFunction<>((await, t1, t2, t3, t4) -> {
                func.accept(await, t1, t2, t3, t4);
                return null;
            }, name);
        }

        public AsyncQuadConsumer(PentaConsumer<Await, T1, T2, T3, T4> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4) {
            return async.apply(t1, t2, t3, t4);
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncPentaFunction<T1, T2, T3, T4, T5, R> implements PentaFunction<T1, T2, T3, T4, T5, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncPentaFunction(HexaFunction<Await, T1, T2, T3, T4, T5, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0], (T2) args[1], (T3) args[2], (T4) args[3], (T5) args[4]), name);
        }

        public AsyncPentaFunction(HexaFunction<Await, T1, T2, T3, T4, T5, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
            return async.apply(new Object[]{t1, t2, t3, t4, t5});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncPentaConsumer<T1, T2, T3, T4, T5> implements PentaFunction<T1, T2, T3, T4, T5, Promise<Void>> {
        private final AsyncPentaFunction<T1, T2, T3, T4, T5, Void> async;

        public AsyncPentaConsumer(HexaConsumer<Await, T1, T2, T3, T4, T5> func, String name) {
            async = new AsyncPentaFunction<>((await, t1, t2, t3, t4, t5) -> {
                func.accept(await, t1, t2, t3, t4, t5);
                return null;
            }, name);
        }

        public AsyncPentaConsumer(HexaConsumer<Await, T1, T2, T3, T4, T5> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
            return async.apply(t1, t2, t3, t4, t5);
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncHexaFunction<T1, T2, T3, T4, T5, T6, R> implements HexaFunction<T1, T2, T3, T4, T5, T6, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncHexaFunction(HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0], (T2) args[1], (T3) args[2], (T4) args[3], (T5) args[4], (T6) args[5]), name);
        }

        public AsyncHexaFunction(HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
            return async.apply(new Object[]{t1, t2, t3, t4, t5, t6});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncHexaConsumer<T1, T2, T3, T4, T5, T6> implements HexaFunction<T1, T2, T3, T4, T5, T6, Promise<Void>> {
        private final AsyncHexaFunction<T1, T2, T3, T4, T5, T6, Void> async;

        public AsyncHexaConsumer(HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func, String name) {
            async = new AsyncHexaFunction<>((await, t1, t2, t3, t4, t5, t6) -> {
                func.accept(await, t1, t2, t3, t4, t5, t6);
                return null;
            }, name);
        }

        public AsyncHexaConsumer(HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
            return async.apply(t1, t2, t3, t4, t5, t6);
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, R> implements HeptaFunction<T1, T2, T3, T4, T5, T6, T7, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncHeptaFunction(OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0], (T2) args[1], (T3) args[2], (T4) args[3], (T5) args[4], (T6) args[5], (T7) args[6]), name);
        }

        public AsyncHeptaFunction(OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7) {
            return async.apply(new Object[]{t1, t2, t3, t4, t5, t6, t7});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncHeptaConsumer<T1, T2, T3, T4, T5, T6, T7> implements HeptaFunction<T1, T2, T3, T4, T5, T6, T7, Promise<Void>> {
        private final AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, Void> async;

        public AsyncHeptaConsumer(OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func, String name) {
            async = new AsyncHeptaFunction<>((await, t1, t2, t3, t4, t5, t6, t7) -> {
                func.accept(await, t1, t2, t3, t4, t5, t6, t7);
                return null;
            }, name);
        }

        public AsyncHeptaConsumer(OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7) {
            return async.apply(t1, t2, t3, t4, t5, t6, t7);
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, R> implements OctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Promise<R>> {
        private final AsyncNargsFunction<R> async;

        public AsyncOctoFunction(NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func, String name) {
            async = new AsyncNargsFunction<>((await, args) -> func.apply(await, (T1) args[0], (T2) args[1], (T3) args[2], (T4) args[3], (T5) args[4], (T6) args[5], (T7) args[6], (T8) args[7]), name);
        }

        public AsyncOctoFunction(NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func) {
            this(func, null);
        }

        public Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8) {
            return async.apply(new Object[]{t1, t2, t3, t4, t5, t6, t7, t8});
        }

        public String getName() {
            return async.getName();
        }
    }

    public class AsyncOctoConsumer<T1, T2, T3, T4, T5, T6, T7, T8> implements OctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Promise<Void>> {
        private final AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Void> async;

        public AsyncOctoConsumer(NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func, String name) {
            async = new AsyncOctoFunction<>((await, t1, t2, t3, t4, t5, t6, t7, t8) -> {
                func.accept(await, t1, t2, t3, t4, t5, t6, t7, t8);
                return null;
            }, name);
        }

        public AsyncOctoConsumer(NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func) {
            this(func, null);
        }

        public Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8) {
            return async.apply(t1, t2, t3, t4, t5, t6, t7, t8);
        }

        public String getName() {
            return async.getName();
        }

    }

    // o------o
    // | def: |
    // o------o
    public <R> AsyncSupplier<R> def(Function<Await, R> func) {
        return new AsyncSupplier<>(func);
    }

    public AsyncRunnable def(Consumer<Await> func) {
        return new AsyncRunnable(func);
    }

    public <T1, R> AsyncFunction<T1, R> def(BiFunction<Await, T1, R> func) {
        return new AsyncFunction<>(func);
    }

    public <T> AsyncConsumer<T> def(BiConsumer<Await, T> func) {
        return new AsyncConsumer<>(func);
    }

    public <T1, T2, R> AsyncBiFunction<T1, T2, R> def(TriFunction<Await, T1, T2, R> func) {
        return new AsyncBiFunction<>(func);
    }

    public <T1, T2> AsyncBiConsumer<T1, T2> def(TriConsumer<Await, T1, T2> func) {
        return new AsyncBiConsumer<>(func);
    }

    public <T1, T2, T3, R> AsyncTriFunction<T1, T2, T3, R> def(QuadFunction<Await, T1, T2, T3, R> func) {
        return new AsyncTriFunction<>(func);
    }

    public <T1, T2, T3> AsyncTriConsumer<T1, T2, T3> def(QuadConsumer<Await, T1, T2, T3> func) {
        return new AsyncTriConsumer<>(func);
    }

    public <T1, T2, T3, T4, R> AsyncQuadFunction<T1, T2, T3, T4, R> def(PentaFunction<Await, T1, T2, T3, T4, R> func) {
        return new AsyncQuadFunction<>(func);
    }

    public <T1, T2, T3, T4> AsyncQuadConsumer<T1, T2, T3, T4> def(PentaConsumer<Await, T1, T2, T3, T4> func) {
        return new AsyncQuadConsumer<>(func);
    }

    public <T1, T2, T3, T4, T5, R> AsyncPentaFunction<T1, T2, T3, T4, T5, R> def(HexaFunction<Await, T1, T2, T3, T4, T5, R> func) {
        return new AsyncPentaFunction<>(func);
    }

    public <T1, T2, T3, T4, T5> AsyncPentaConsumer<T1, T2, T3, T4, T5> def(HexaConsumer<Await, T1, T2, T3, T4, T5> func) {
        return new AsyncPentaConsumer<>(func);
    }

    public <T1, T2, T3, T4, T5, T6, R> AsyncHexaFunction<T1, T2, T3, T4, T5, T6, R> def(HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func) {
        return new AsyncHexaFunction<>(func);
    }

    public <T1, T2, T3, T4, T5, T6> AsyncHexaConsumer<T1, T2, T3, T4, T5, T6> def(HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func) {
        return new AsyncHexaConsumer<>(func);
    }

    public <T1, T2, T3, T4, T5, T6, T7, R> AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, R> def(OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func) {
        return new AsyncHeptaFunction<>(func);
    }

    public <T1, T2, T3, T4, T5, T6, T7> AsyncHeptaConsumer<T1, T2, T3, T4, T5, T6, T7> def(OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func) {
        return new AsyncHeptaConsumer<>(func);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, R> AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, R> def(NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func) {
        return new AsyncOctoFunction<>(func);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8> AsyncOctoConsumer<T1, T2, T3, T4, T5, T6, T7, T8> def(NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func) {
        return new AsyncOctoConsumer<>(func);
    }


    public <R> AsyncSupplier<R> def(String name, Function<Await, R> func) {
        return new AsyncSupplier<>(func, name);
    }

    public AsyncRunnable def(String name, Consumer<Await> func) {
        return new AsyncRunnable(func, name);
    }

    public <T1, R> AsyncFunction<T1, R> def(String name, BiFunction<Await, T1, R> func) {
        return new AsyncFunction<>(func, name);
    }

    public <T> AsyncConsumer<T> def(String name, BiConsumer<Await, T> func) {
        return new AsyncConsumer<>(func, name);
    }

    public <T1, T2, R> AsyncBiFunction<T1, T2, R> def(String name, TriFunction<Await, T1, T2, R> func) {
        return new AsyncBiFunction<>(func, name);
    }

    public <T1, T2> AsyncBiConsumer<T1, T2> def(String name, TriConsumer<Await, T1, T2> func) {
        return new AsyncBiConsumer<>(func, name);
    }

    public <T1, T2, T3, R> AsyncTriFunction<T1, T2, T3, R> def(String name, QuadFunction<Await, T1, T2, T3, R> func) {
        return new AsyncTriFunction<>(func, name);
    }

    public <T1, T2, T3> AsyncTriConsumer<T1, T2, T3> def(String name, QuadConsumer<Await, T1, T2, T3> func) {
        return new AsyncTriConsumer<>(func, name);
    }

    public <T1, T2, T3, T4, R> AsyncQuadFunction<T1, T2, T3, T4, R> def(String name, PentaFunction<Await, T1, T2, T3, T4, R> func) {
        return new AsyncQuadFunction<>(func, name);
    }

    public <T1, T2, T3, T4> AsyncQuadConsumer<T1, T2, T3, T4> def(String name, PentaConsumer<Await, T1, T2, T3, T4> func) {
        return new AsyncQuadConsumer<>(func, name);
    }

    public <T1, T2, T3, T4, T5, R> AsyncPentaFunction<T1, T2, T3, T4, T5, R> def(String name, HexaFunction<Await, T1, T2, T3, T4, T5, R> func) {
        return new AsyncPentaFunction<>(func, name);
    }

    public <T1, T2, T3, T4, T5> AsyncPentaConsumer<T1, T2, T3, T4, T5> def(String name, HexaConsumer<Await, T1, T2, T3, T4, T5> func) {
        return new AsyncPentaConsumer<>(func, name);
    }

    public <T1, T2, T3, T4, T5, T6, R> AsyncHexaFunction<T1, T2, T3, T4, T5, T6, R> def(String name, HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func) {
        return new AsyncHexaFunction<>(func, name);
    }

    public <T1, T2, T3, T4, T5, T6> AsyncHexaConsumer<T1, T2, T3, T4, T5, T6> def(String name, HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func) {
        return new AsyncHexaConsumer<>(func, name);
    }

    public <T1, T2, T3, T4, T5, T6, T7, R> AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, R> def(String name, OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func) {
        return new AsyncHeptaFunction<>(func, name);
    }

    public <T1, T2, T3, T4, T5, T6, T7> AsyncHeptaConsumer<T1, T2, T3, T4, T5, T6, T7> def(String name, OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func) {
        return new AsyncHeptaConsumer<>(func, name);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8, R> AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, R> def(String name, NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func) {
        return new AsyncOctoFunction<>(func, name);
    }

    public <T1, T2, T3, T4, T5, T6, T7, T8> AsyncOctoConsumer<T1, T2, T3, T4, T5, T6, T7, T8> def(String name, NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func) {
        return new AsyncOctoConsumer<>(func, name);
    }

    // special:
    public AsyncRunnable defRunnable(Consumer<Await> func) {
        return new AsyncRunnable(func);
    }

    public AsyncRunnable defRunnable(String name, Consumer<Await> func) {
        return new AsyncRunnable(func, name);
    }
    // this took forever to type
}

















