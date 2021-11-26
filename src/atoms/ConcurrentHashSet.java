package atoms;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ConcurrentHashSet<T> implements Iterable<T> {
    private ConcurrentHashMap<T, T> map = new ConcurrentHashMap<>();
    public Iterator<T> iterator(){
        return map.keySet().iterator();
    }

    public boolean contains(T value){
        return map.keySet().contains(value);
    }
    public boolean isEmpty(){
        return map.isEmpty();
    }
    public boolean add(T value){
        return map.put(value, value) == null;
    }

    public T remove(T value){
        return map.remove(value);
    }

    public int size(){
        return map.size();
    }
    public void clear(){
        map.clear();
    }
    public Stream<T> stream(){
        return map.keySet().stream();
    }
    public boolean removeIf(Predicate<T> test){
        final var iter = map.keySet().iterator();
        boolean removed = false;

        while(iter.hasNext()){
            final var current = iter.next();
            if (test.test(current)){
                iter.remove();
                removed = true;
            }
        }

        return removed;
    }
}
