package exceptionsPlus;

import java.util.HashSet;
import java.util.Set;

// Wrapper around any exception to ensure that is unchecked.
public class UncheckedWrapper extends RuntimeException {
	private static final long serialVersionUID = 1L;
	/**
	 * The original exception that was thrown.
	 */
	public Throwable getOriginal() {
		return getCause();
	}
	public UncheckedWrapper(Throwable original) {
		super(original);
	}
	@Override
	public String getMessage() {
		if (getOriginal() == null)
			return null;
		else
			return getOriginal().toString();
	}
	
	/**
	 * @returns The given exception wrapped in an UncheckedWrapper or just the
	 * exception itself if it is already of the instance UncheckedWrapper. If
	 * the exception is an UncheckedWrapper and so is it's original. The
	 * original will be returned. If the original's original is also of the
	 * instance unchecked wrapper, that will be returned, etc...
	 */
	public static UncheckedWrapper uncheckify(Throwable exception) {
		if (exception instanceof UncheckedWrapper uw) {
			// set for checking for loops
			Set<UncheckedWrapper> previous = new HashSet<>();
			previous.add(uw);
			// loop through looking for the bottom
			UncheckedWrapper current = uw;
			while(current.getOriginal() instanceof UncheckedWrapper uw2) {
				// check for loops
				if (previous.contains(uw2))
					return new UncheckedWrapper(null);
				
				current = uw2;
				
				previous.add(uw2);
			}
			
			// should return deepest nested UncheckedWrapper.
			return current;
		}
		else {
			return new UncheckedWrapper(exception);
		}
	}
	
	@Override
	public String toString() {
		return "Unchecked(Throwable)Wrapper: { " + (getOriginal() == null ? "null" : getOriginal().toString()) + " }";
	}
}
