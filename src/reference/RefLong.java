package reference;

public class RefLong{
	private long value;
	public RefLong(long value) {
		this.value = value;
	}
	public long get() { return value; }
	public void set(long value) { this.value = value; }
	
	public long incrementAndGet() { return ++value; }
	public long getAndIncrement() { return value++; }
	public long decrementAndGet() { return --value; }
	public long getAndDecrement() { return value--; }
}
