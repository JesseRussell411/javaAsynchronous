package asynchronous;

public class UncheckedInterruptedException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	public final InterruptedException original;
	public UncheckedInterruptedException(InterruptedException original) {
		this.original = original;
	}
}
