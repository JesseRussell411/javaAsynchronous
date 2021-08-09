package exceptionsPlus;

// Wrapper around any exception to ensure that is unchecked.
public class UncheckedException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	/**
	 * The original exception that was thrown.
	 */
	private final Exception original;
	public Exception getOriginal() {
		return original;
	}
	public UncheckedException(Exception original) {
		super(original.getMessage(), original.getCause());
		this.original = original;
	}
}
