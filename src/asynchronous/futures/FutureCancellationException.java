package asynchronous.futures;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

public class FutureCancellationException extends CancellationException{
	private static final long serialVersionUID = 1L;
	private final Future<?> future;
	
	public Future<?> getPromise() { return future; }
	
	public FutureCancellationException(Future<?> future, String message) {
		super(message);
		this.future = future;
	}
	public FutureCancellationException(Future<?> future) {
		this.future = future;
	}
}
