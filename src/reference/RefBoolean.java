package reference;

public class RefBoolean{
	private boolean value;
	public RefBoolean(boolean value) {
		this.value = value;
	}
	public boolean get() { return value; }
	public void set(boolean value) { this.value = value; }
	
	public void toggle() { value = !value; }
}