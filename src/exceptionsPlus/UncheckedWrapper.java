package exceptionsPlus;

// Wrapper around any exception to ensure that is unchecked.
public class UncheckedWrapper extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	/**
	 * The original exception that was thrown.
	 */
	private final Exception original;
	public Exception getOriginal() {
		return original;
	}
	public UncheckedWrapper(Exception original) {
		super(original.getMessage(), original.getCause());
		this.original = original;
	}
}
