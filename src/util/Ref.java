package util;

/**
 * Insultingly simple class. Stores a reference to a value of type T.
 */
public class Ref<T> {
	private T value;
	public T get() { return value; }
	public void set(T value) { this.value = value; }
	public Ref(T value) {
		this.value = value;
	}
	public Ref() {
		this.value = null;
	}
}
