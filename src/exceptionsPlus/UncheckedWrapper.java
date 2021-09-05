package exceptionsPlus;

// Wrapper around any exception to ensure that is unchecked.
public class UncheckedWrapper extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private final Throwable original;
	/**
	 * The original exception that was thrown.
	 */
	public Throwable getOriginal() {
		return original;
	}
	public UncheckedWrapper(Throwable original) {
		super(original.getMessage(), original.getCause());
		this.original = original;
	}
	
	/**
	 * @returns The given exception wrapped in an UncheckedWrapper or just the exception itself if it is already of the instance UncheckedWrapper. If the exception is an UncheckedWrapper and so is it's original. The original will be returned. If the original's original is also of the instance unchecked wrapper, that will be returned, etc...
	 */
	public static UncheckedWrapper uncheckify(Throwable exception) {
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
	
	@Override
	public String toString() {
		return "Unchecked(Throwable)Wrapper: { " + (original == null ? "null" : original.toString()) + " }";
	}
}
