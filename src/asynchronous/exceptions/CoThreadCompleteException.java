package asynchronous.exceptions;

/**
 * Signals that a CoThread has completed.
 */
public class CoThreadCompleteException extends Exception {
	private static final long serialVersionUID = 1L;

	CoThreadCompleteException(){
		super();
	}
}
