package asynchronous.exceptions;

import exceptionsPlus.UncheckedWrapper;

public class UncheckedInterruptedException extends UncheckedWrapper {
	private static final long serialVersionUID = 1L;
	public InterruptedException getOriginal() {
		return (InterruptedException)getOriginal();
	}
	public UncheckedInterruptedException(InterruptedException original) {
		super(original);
	}
}
