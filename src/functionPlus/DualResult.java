package functionPlus;
import java.util.function.*;

/**
 * Represents a result that could be one of two types. Like for example:
 * DualResult<Integer, String> which could represent the result of an ID lookup
 * that is likely to fail with an error message; possibly because the user
 * misspelled the name of whatever the ID lookup is trying to look-up.
 * 
 * It is instantiated using the asA() and asB() static methods.
 * @author jesse
 */
public class DualResult<A, B> {
	public final A valueA;
	public final B valueB;
	public final boolean isB;
	private DualResult(A valueA, B valueB, boolean isB) {
		this.valueA = valueA;
		this.valueB = valueB;
		this.isB = isB;
	}
	/**
	 * @return A DualResult defined by the given A-type value.
	 */
	public static <A, B> DualResult<A, B> asA(A value) {
		return new DualResult<A, B>(value, null, false);
	}
	/**
	 * @return A DualResult defined by the given B-type value.
	 */
	public static <A, B> DualResult<A, B> asB(B value){
		return new DualResult<A, B>(null, value, true);
	}
	
	/**
	 * Match the result to a value.
	 * @param ifA Ran if the DualResult is of type A.
	 * @param ifB Ran if the DualResult is of type B.
	 * @return The outcome of whichever function (ifA/ifB) was ran.
	 */
	public <R> R match(Function<A, R> ifA, Function<B, R> ifB) {
		if(isB)
			return ifB.apply(valueB);
		else
			return ifA.apply(valueA);
	}
	/**
	 * If the DualResult is of type A: Supplies the given consumer with the resulting value.
	 * @return The DualResult to allow chaining.
	 */
	public DualResult<A, B> ifA(Consumer<A> func){
		if (!isB)
			func.accept(valueA);
		
		return this;
	}
	/**
	 * If the DualResult is of type B: Supplies the given consumer with the resulting value.
	 * @return The DualResult to allow chaining.
	 */
	public DualResult<A, B> ifB(Consumer<B> func){
		if(isB)
			func.accept(valueB);
		
		return this;
	}
	/**
	 * @return The value of type A if the DualResult is of type A or the result of getAlternative otherwise.
	 * @param getAlternative Given the value of type B in order to produce an alternative to the value of type A.
	 */
	public A aOr(Function<B, A> getAlternative) {
		if(isB)
			return getAlternative.apply(valueB);
		else
			return valueA;
	}
	/**
	 * @return The value of type B if the DualResult is of type B or the result of getAlternative otherwise.
	 * @param getAlternative Given the value of type A in order to produce an alternative to the value of type B.
	 */
	public B bOr(Function<A, B> getAlternative) {
		if(isB)
			return valueB;
		else
			return getAlternative.apply(valueA);
	}
}
