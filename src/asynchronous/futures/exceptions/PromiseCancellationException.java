package asynchronous.futures.exceptions;

import asynchronous.futures.Promise;

import java.util.concurrent.CancellationException;

public class PromiseCancellationException extends CancellationException{
	private static final long serialVersionUID = 1L;
	private final Promise<?> promise;
	
	public Promise<?> getPromise() { return promise; }
	
	public PromiseCancellationException(Promise<?> promise, String message) {
		super(message);
		this.promise = promise;
	}
	public PromiseCancellationException(Promise<?> promise) {
		this.promise = promise;
	}
}
