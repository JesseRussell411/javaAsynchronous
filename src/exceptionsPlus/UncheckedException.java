package exceptionsPlus;

public class UncheckedException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final Exception original;
	public Exception getOriginal() {
		return original;
	}
	public UncheckedException(Exception original) {
		super(original.getMessage(), original.getCause());
		this.original = original;
	}
}
