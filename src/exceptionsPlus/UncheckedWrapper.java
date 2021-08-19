package exceptionsPlus;

// Wrapper around any exception to ensure that is unchecked.
public class UncheckedWrapper extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private final Exception original;
	/**
	 * The original exception that was thrown.
	 */
	public Exception getOriginal() {
		return original;
	}
	public UncheckedWrapper(Exception original) {
		super(original.getMessage(), original.getCause());
		this.original = original;
	}
	
	/**
	 * @returns The given exception wrapped in an UncheckedWrapper or just the exception itself if it is already of the instance UncheckedWrapper. If the exception is an UncheckedWrapper and so is it's original. The original will be returned. If the original's original is also of the instance unchecked wrapper, that will be returned, etc...
	 */
	public static UncheckedWrapper uncheckify(Exception exception) {
		if (exception instanceof UncheckedWrapper) {
			UncheckedWrapper current = (UncheckedWrapper)exception;
			while(current.getOriginal() instanceof UncheckedWrapper) {
				current = (UncheckedWrapper)current.getOriginal();
			}
			return current;
		}
		else {
			return new UncheckedWrapper(exception);
		}
	}
}
