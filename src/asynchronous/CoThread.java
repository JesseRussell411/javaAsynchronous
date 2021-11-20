package asynchronous;

import asynchronous.futures.*;

import java.util.function.*;

/**
 * A thread that acts like a coroutine. Like a normal Thread, this class's
 * constructor takes a functional class to run concurrently; howerver, this
 * class supplies that functional class with an instance of a class called
 * Yield. Yield has a method called accept which takes a value of type T and
 * then blocks until the CoThread is signaled to run again by its "run" method.
 * The run method returns a promise which is settled by the CoThread yielding,
 * dying (completing execution), or throwing an error.
 *
 * @param <T> The type of the value yielded with.
 * @author Jesse Russell
 */
public class CoThread<T> implements AutoCloseable {
    private final Thread thread;
    private final Yield yields = new Yield();
    private volatile Deferred<T> deferred;

    // flags:
    private volatile boolean running = false;
    private volatile boolean started = false;
    private volatile boolean dead = false;

    // flag getters:

    /**
     * Whether the run method has been called and the CoThread has not yet yielded or died.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Whether the run method has been called at least once.
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Whether the CoThread has thrown an error, or finished execution. In either case, it will no longer yield any more values, or even run at all.
     */
    public boolean isDead() {
        return dead;
    }

    public CoThread(Consumer<Yield> func, String name) {
        thread = new Thread(makeBody(func), name);
    }

    public CoThread(Consumer<Yield> func) {
        thread = new Thread(makeBody(func));
    }

    private Runnable makeBody(Consumer<Yield> func) {
        return () -> {
            synchronized (yields) {
                try {
                    func.accept(yields);
                } catch (Throwable e) {
                    deferred.settle().reject(e);
                } finally {
                    running = false;
                    dead = true;
                    deferred.settle().cancel();
                }
            }
        };
    }

    /**
     * Runs the CoThread.
     *
     * @return Promise which is Settled when the thread yields, dies, or throws an error. If it yields: the promise is
     * resolved with the value yielded. If it dies: the promise is canceled. If it throws and error: the promise is
     * rejected with the error.
     */
    public synchronized Promise<T> run() {
        if (running || dead)
            return deferred.promise();

        synchronized (yields) {
            if (running || dead)
                return deferred.promise();


            deferred = new Deferred<T>();
            running = true;

            if (!started) {
                thread.start();
                started = true;
            }

            yields.notifyAll();

            return deferred.promise();
        }
    }

    /**
     * Functional class for yielding with a value. Blocks until the next call to run.
     */
    public class Yield {
        private Yield() {
        }

        /**
         * Yield with the given value. Blocks until the next call to run.
         *
         * @throws InterruptedException As usual, be sure to handle this
         *                              exception appropriately and halt execution. This is how the CoThread
         *                              is closed.
         */
        public synchronized void accept(T value) throws InterruptedException {
            running = false;
            deferred.settle().resolve(value);

            notifyAll();
            while (!running) {
                wait();
            }
        }
    }

    @Override
    public void close() {
        thread.interrupt();
    }
}
