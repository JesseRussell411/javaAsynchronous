package message;

import java.util.ArrayList;

import java.util.List;
import java.util.function.*;

import asynchronous.futures.*;

/**
 * Like an AtomicReference but can be given callbacks to run when changed.
 * @author jesse
 *
 * @param <T>
 */
public class VolitileMessenger<T> {
	public static class ValueAndNext<T>{
		public final T value;
		public final Promise<ValueAndNext<T>> next;
		public ValueAndNext(T value, Promise<ValueAndNext<T>> next){
			this.value = value;
			this.next = next;
		}
	}
	private volatile T value;
	private volatile Deferred<ValueAndNext<T>> deferredNext = new Deferred<>();
	private final List<Consumer<T>> onChange = new ArrayList<>();
	
	public VolitileMessenger(T initialValue) {
		value = initialValue;
	}
	public VolitileMessenger() {
		this(null);
	}
	
	/**
	 * @return The current value.
	 */
	public T get() {
		return value;
	}
	
	/**
	 * @return A promise that resolves the next time the value is changes.
	 */
	public Promise<ValueAndNext<T>> next() {
		return deferredNext.promise();
	}
	
	/**
	 * Sets the value
	 */
	public synchronized void set(T value) {
		this.value = value;
	
		for(final var action : onChange)
				action.accept(value);
		
		final var deferredCurrent = deferredNext;
		
		deferredNext = new Deferred<>();
		deferredCurrent.settle().resolve(new ValueAndNext<T>(value, deferredNext.promise()));
		
		this.notifyAll();
	}
	
	public synchronized void onChange(Consumer<T> action) {
		onChange.add(action);
	}
}
