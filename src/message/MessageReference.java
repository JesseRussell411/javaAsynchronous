package message;

import java.util.ArrayList;
import java.util.concurrent.atomic.*;

import java.util.List;
import java.util.function.*;

import asynchronous.futures.*;

public class MessageReference<T> {
	private volatile T value = null;
	private final List<Consumer<T>> onSet = new ArrayList<>();
	public MessageReference(T value) {
		this.value = value;
	}
	
	public synchronized T get() {
		return value;
	}
	
	public synchronized void set(T value) {
		this.value = value;
		
		for(final var action : onSet)
			action.accept(value);
		
		this.notifyAll();
	}
	
	public synchronized void onSet(Consumer<T> action) {
		onSet.add(action);
	}
}
