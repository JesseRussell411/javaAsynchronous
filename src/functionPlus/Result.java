package functionPlus;
import java.util.function.*;

/**
 * Represents the result of something. Not meant to be null. The field: 'undefined' references whether there was not a result.
 * @author jesse
 *
 * @param <T>
 */
public class Result<T> {
	/**
	 * The resulting value.
	 */
	public final T value;
	/**
	 * Whether there is no resulting value.
	 */
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
	 * Match the result to a value.
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
	 * @return The value of the result or an alternative from the given supplier if the value is not defined.
	 */
	public T or(Supplier<T> getAlternative) {
		if (undefined)
			return getAlternative.get();
		else
			return value;
	}
	/**
	 * Runs the given function if the Result is defined.
	 * @return The Result to allow for chaining
	 */
	public Result<T> ifDefined(Consumer<T> ifDefined){
		if (!undefined)
			ifDefined.accept(value);
		
		return this;
	}
	/**
	 * Runs the given function if the Result is not defined.
	 * @param ifNotDefined
	 * @return The Result to allow for chaining.
	 */
	public Result<T> ifNotDefined(Runnable ifNotDefined){
		if (undefined)
			ifNotDefined.run();
		
		return this;
	}
}
