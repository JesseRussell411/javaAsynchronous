package functionPlus;
import java.util.function.*;

/**
 * Represents the result of something. Not meant to be null. The field:
 * "undefined" references whether there was no result. Methods such as "match"
 * are provided for control flow.
 * @author Jesse Russell
 *
 * @param <T> The type of the resulting value.
 */
public class Result<T> {
	/** The resulting value. */
	public final T value;
	/** Whether there is no resulting value. */
	public final boolean undefined;
	
	public Result(T value) {
		this.value = value;
		undefined = false;
	}
	public Result() {
		value = null;
		undefined = true;
	}
	
	/**
	 * Match the result to a function with a return value.
	 * @param ifDefined Ran if the result is defined.
	 * @param ifNotDefined Ran if the result is not defined.
	 * @return The outcome of whichever function (ifDefined/ifNotDefined) was called.
	 */
	public <R> R matchApply(Function<T, R> ifDefined, Supplier<R> ifNotDefined) {
		if (undefined)
			return ifNotDefined.get();
		else
			return ifDefined.apply(value);
	}
	
	/**
	 * Match the result to a function.
	 * @param ifDefined Ran if the result is defined.
	 * @param ifNotDefined Ran if the result is not defined.
	 */
	public void matchAccept(Consumer<T> ifDefined, Runnable ifNotDefined) {
		if (undefined)
			ifNotDefined.run();
		else
			ifDefined.accept(value);
	}
	/**
	 * Match the result to a function with a return.
	 * @param ifDefined Ran if the result is defined.
	 * @param ifNotDefined Ran if the result is not defined.
	 * @return The outcome of whichever function (ifDefined/ifNotDefined) was called.
	 */
	public <R> R match(Function<T, R> ifDefined, Supplier<R> ifNotDefined) {
		if (undefined)
			return ifNotDefined.get();
		else
			return ifDefined.apply(value);
	}
	
	/**
	 * Match the result to a function.
	 * @param ifDefined Ran if the result is defined.
	 * @param ifNotDefined Ran if the result is not defined.
	 */
	public void match(Consumer<T> ifDefined, Runnable ifNotDefined) {
		if (undefined)
			ifNotDefined.run();
		else
			ifDefined.accept(value);
	}
	
	/**
	 * @return The value of the result or the given alternative if the value is
	 * not defined.
	 */
	public T or(T alternative) {
		if (undefined)
			return alternative;
		else
			return value;
	}
	
	/**
	 * @return The value of the result or the result of the given Supplier if the value is not defined.
	 * @param getAlternative Ran if the value of the result is not defined.
	 */
	public T orGet(Supplier<T> getAlternative) {
		if (undefined)
			return getAlternative.get();
		else
			return value;
	}
	
	/**
	 * Runs the given function if the Result is defined.
	 * @return The Result instance to allow for chaining
	 */
	public Result<T> ifDefined(Consumer<T> ifDefined) {
		if (!undefined)
			ifDefined.accept(value);
		
		return this;
	}
	
	/**
	 * Runs the given function if the Result is not defined.
	 * @param ifNotDefined
	 * @return The Result instance to allow for chaining.
	 */
	public Result<T> ifNotDefined(Runnable ifNotDefined) {
		if (undefined)
			ifNotDefined.run();
		
		return this;
	}
}
