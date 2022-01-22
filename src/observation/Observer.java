package observation;

import collections.ConcurrentHashSet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Observer<T> {
    private final Observable<T> subject;

    public Observer(Observable<T> subject) {
        this.subject = subject;
        subject.addObserver(this);
    }

    void update(T value) {
        try {
            closeLock.readLock().lock();
            if (closed.get()) return;
            updateOnceReactions(value);
            updateAsyncUntilReactions(value);
            updateSyncedUntilReactions(value);
        } finally {
            closeLock.readLock().unlock();
        }
    }

    // ===== close-ability ======
    private final ReadWriteLock closeLock = new ReentrantReadWriteLock();
    private final AtomicBoolean closed = new AtomicBoolean();

    void closeSelf() {
        try {
            closeLock.writeLock().lock();

            clearCallbacks();
            subject.removeObserver(this);
            closed.set(true);
        } finally {
            closeLock.writeLock().unlock();
        }

    }

    // === callbacks ===
    private ConcurrentHashSet<Consumer<T>> onceCallbacks = new ConcurrentHashSet<>();
    private Map<Predicate<T>, AtomicBoolean> asyncUntilCallbacks = new ConcurrentHashMap<>();
    private Map<Predicate<T>, AtomicBoolean> syncedUntilCallbacks = new ConcurrentHashMap<>();

    private void clearCallbacks() {
        onceCallbacks.clear();
        asyncUntilCallbacks.clear();
        syncedUntilCallbacks.clear();
    }

    public Runnable reactOnce(Consumer<T> reaction) {
        try {
            closeLock.readLock().lock();
            if (closed.get()) throw new ClosedObserverException(this);

            onceCallbacks.add(reaction);
            return () -> {
                onceCallbacks.remove(reaction);
            };
        } finally {
            closeLock.readLock().unlock();
        }
    }

    public Runnable reactUntil_async(Predicate<T> reaction) {
        try {
            closeLock.readLock().lock();
            if (closed.get()) throw new ClosedObserverException(this);

            final var flag = new AtomicBoolean(true);
            asyncUntilCallbacks.put(reaction, flag);
            return () -> {
                flag.set(false);
                asyncUntilCallbacks.remove(reaction);
            };
        } finally {
            closeLock.readLock().unlock();
        }
    }

    public Runnable reactUntil_synced(Predicate<T> reaction) {
        try {
            closeLock.readLock().lock();
            if (closed.get()) throw new ClosedObserverException(this);

            final var flag = new AtomicBoolean(true);
            syncedUntilCallbacks.put(reaction, flag);
            return () -> {
                flag.set(false);
                syncedUntilCallbacks.remove(reaction);
            };
        } finally {
            closeLock.readLock().unlock();
        }
    }

    public Runnable react(Consumer<T> reaction) {
        return reactUntil_async(value -> {
            reaction.accept(value);
            return false;
        });
    }

    public Runnable reactUntil(Predicate<T> reaction) {
        return reactUntil_synced(reaction);
    }

    private void updateOnceReactions(T value) {
        final var iter = onceCallbacks.iterator();

        while (iter.hasNext()) {
            final var next = iter.next();
            final var reaction = onceCallbacks.getAndRemove(next);
            if (reaction != null) {
                reaction.accept(value);
            }
        }
    }

    private void updateAsyncUntilReactions(T value) {
        final var iter = asyncUntilCallbacks.entrySet().iterator();

        while (iter.hasNext()) {
            final var next = iter.next();
            final var reaction = next.getKey();
            final var flag = next.getValue();
            if (flag.get() && reaction.test(value)) {
                flag.set(false);
                iter.remove();
            }
        }
    }

    private void updateSyncedUntilReactions(T value) {
        final var iter = syncedUntilCallbacks.entrySet().iterator();

        while (iter.hasNext()) {
            final var next = iter.next();
            final var flag = next.getValue();
            final var reaction = next.getKey();

            if (flag.get()) {
                synchronized (reaction) {
                    if (flag.get() && reaction.test(value)) {
                        flag.set(false);
                        iter.remove();
                    }
                }
            }
        }

    }
}
