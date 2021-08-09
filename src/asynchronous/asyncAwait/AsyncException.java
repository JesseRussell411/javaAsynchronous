package asynchronous.asyncAwait;

import exceptionsPlus.UncheckedException;

/**
 * Throne when an exception occurs inside an await call.
 * Simply wraps the original exception to ensure that it is unchecked.
 * Using instanceof to match the original exception to it's type is recommended.
 */
public class AsyncException extends UncheckedException{
	private static final long serialVersionUID = 1L;
	AsyncException(Exception original) {
		super(original);
	}
}
