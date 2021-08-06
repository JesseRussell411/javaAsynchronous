package asynchronous;

import exceptionsPlus.UncheckedException;

public class UncheckedInterruptedException extends UncheckedException {
	private static final long serialVersionUID = 1L;
	public InterruptedException getOriginal() {
		return (InterruptedException)getOriginal();
	}
	public UncheckedInterruptedException(InterruptedException original) {
		super(original);
	}
}
