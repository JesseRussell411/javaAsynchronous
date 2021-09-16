package reference;

public class RefInt{
	private int value;
	public RefInt(int value) {
		this.value = value;
	}
	public int get() { return value; }
	public void set(int value) { this.value = value; }
	
	public int incrementAndGet() { return ++value; }
	public int getAndIncrement() { return value++; }
	public int decrementAndGet() { return --value; }
	public int getAndDecrement() { return value--; }
}
