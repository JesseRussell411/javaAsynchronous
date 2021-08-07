package asynchronous.asyncAwait;

import exceptionsPlus.UncheckedException;

public class AsyncException extends UncheckedException{
	private static final long serialVersionUID = 1L;
	public AsyncException(Exception original) {
		super(original);
	}
}
