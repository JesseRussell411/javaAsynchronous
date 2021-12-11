package data;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ConcurrentHashSet<T> implements Set<T> {
    private final ConcurrentHashMap<T, T> map = new ConcurrentHashMap<>();


    public Iterator<T> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public Object[] toArray() {
        return map.keySet().toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return map.keySet().toArray(a);
    }

    public boolean contains(Object value) {
        return map.containsKey(value);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean add(T value) {
        return map.put(value, value) == null;
    }
    public T addAndGet(T value) {
        add(value);
        return value;
    }

    public T getAndRemove(T value) {
        return map.remove(value);
    }

    public boolean remove(Object value) {
        return map.remove(value) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return map.keySet().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return map.keySet().addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return map.keySet().retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return map.keySet().removeAll(c);
    }


    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    public Stream<T> stream() {
        return map.keySet().stream();
    }

    public boolean removeIf(Predicate<? super T> test) {
        final var iter = map.keySet().iterator();
        boolean removed = false;

        while (iter.hasNext()) {
            final var current = iter.next();
            if (test.test(current)) {
                iter.remove();
                removed = true;
            }
        }

        return removed;
    }
}
