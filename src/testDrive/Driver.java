package testDrive;
import java.util.Random;
import java.util.function.*;

import asynchronous.*;
import asynchronous.asyncAwait.*;
import asynchronous.futures.Promise;
import exceptionsPlus.UncheckedWrapper;

public class Driver {
	public static Async async = new Async();
	
	// example of defining an async function as a class method:
	public static Promise<String> AsyncTriConcatenater(Supplier<Promise<Object>> a, Supplier<Promise<Object>> b, Supplier<Promise<Object>> c){
//		// without async
//		return a.get().asyncThen(ar -> 
//					 b.get().asyncThen(br -> 
//							   c.get().then(cr ->
//									ar.toString()
//									+ br.toString()
//									+ cr.toString())));
		
//		// with async
//		return async.def(
//			await -> 
//			await.apply(a.get()).toString() +
//			await.apply(b.get()).toString() +
//			await.apply(c.get()).toString()
//		).get();
			
		// with async parallel:
		final var ap = a.get();
		final var bp = b.get();
		final var cp = c.get();
		
		return async.def(await ->
			await.apply(ap).toString() +
			await.apply(bp).toString() +
			await.apply(cp).toString()
		).get();
	}
	
	public static void main(String[] args) throws InterruptedException{
		AsyncTriConcatenater(() -> Promise.resolved("----------hello "), () -> Promise.resolved("world"), () -> Promise.resolved("!")).thenAccept(r -> System.out.println(r));
		
		// *** Out-dated documentation, sorry about that
		// Instructions:
		// ------------
		// to make an async function, instantiate the Async class or one of it's siblings: Async1, Async2, Async3... etc. if you want the function to have parameters.
		// Async1 takes one parameter, Async2 takes two, etc. Async of course takes no parameters. Use AsyncVoid if there's nothing to return.
		// The constructor takes a lambda which takes the await functional class (Async.Await) and the appropriate number and types of parameters. The constructor also optionally
		// takes a name (which is given to the thread running the function when it's called). The Async class implements Supplier<T>. Async1 implements Function<T1, R>.
		// Async2 implements BiFunction<T1, T2, R>, etc. To call an async function, just use the appropriate functional class method. get() for Async, apply() for Async1, Async2, etc.
		// Each call returns a promise. The function won't actually be executed until Async.execute is called so do that at the end of your main method.
		
		// I modeled all of this after javascript. Because I personally think that javascript is a beautiful language when it comes to asynchronous and functional programming.
		// Just ignore everything else.
		
		// for instance:
		
		// in javascript:
		//
		// const foo = async() => "bar"
		// 
		// ... later, in an async function:
		// cosnt bar = await foo();
		
		// in java:
		//
		// final var foo = new Async<String>(await -> "bar");
		//
		// ... later, in an Async functional class:
		// final var bar = await.apply(foo.get());
		//
		// ... even later, probably at the end of main:
		// Async.execute();
		
		
		
		// in javascript:
		//
		// const fetchStuffFromServer = async (id) => {
		//     return await httpFetchFunction("http://stuffServer/stuff/" + id);
		// }
		//
		// ... later, in an async function:
		// const stuff = await fetchStuffFromServer(42);
		
		// in java:
		//
		// final var fetchStuffFromServer = new Async1<Integer, Response>((await, id) -> {
		//     return await.apply(httpFetchFunction("http://stuffServer/stuff/" + id));
		// }
		//
		// ... later, in an Async functional class:
		// final var stuff = await.apply(fetchStuffFromServer.apply(42));
		//
		// ... even later, probably at the end of main:
		// Async.execute();
		
		
		
		// in javascript:
		// return new Promise(resolve => resolve(42));
		
		// in java:
		// return new Promise(resolve -> resolve.accept(42));
		
		// in javascript:
		// somePromise.then(r => console.log(r));
		
		// in java:
		// somePromise.then(r -> { System.out.println(r); });
		
		// And yes I made my own promise class because, well, completableFuture looked real confusing so naturally I would rather create my own than learn how to use the one that already exists.
		// The promise class acts almost exactly like the one in javascript of course.

		
		// over complicated hello world example:
		// ------------------------------------
//		final var getHelloWorld = async.def((await) -> {
//			printCrap.get();
//			return await.apply(supplyHelloWorld.apply(2));
//			});
		final var getHello = async.def("getHello", await -> {
			// sleep for a bit
			await.sleep(1000);
			
			// get around to returning hello
			return "hello";
		});
		
		final var getSpace = async.def("getSpace", await -> {
			//sleep for a bit
			await.sleep(500);
			
			// get around to returning a space
			return " ";
		});
		
		final var getWorld = async.def("getWorld", await -> {		
			// sleep for a bit
			await.sleep(900);
			
			// get around to returning world
			return "world";
		});
		
		final var getHelloworld = async.def("getHelloworld", await -> {
			// get the promises from each function
			final var hello = getHello.get();
			final var space = getSpace.get();
			final var world = getWorld.get();
			
			// await those promises
			return await.apply(hello) +
					await.apply(space) +
					await.apply(world);
		});
		
		final var main = async.def("main", await -> {
			System.out.println("Getting hello world...");
			System.out.println(await.apply(getHelloworld.get()));
			System.out.println("Hello world has been gotten.");
		});
		
		
		System.out.println("Hello world example: ");
		main.get();
		// execute doesn't HAVE to be called at the end of main. It can really be called anywhere. But beware, it blocks until all async function calls are complete.
		async.execute();
		
		
		
		
		
		// Error handling example/test:
		// ---------------------------
		
		// Errors propagate up just like in javascript, but they do have to be wrapped in UncheckedWrapper (a RuntimeException)
		// because checked exceptions get really annoying when you're using lambdas.
		final var throwSomething = async.def("throwSomething", (Consumer<Async.Await>)(await -> {
			throw new NullPointerException("This pointer doesn't exist. Oh, wait that means void! sorry");
		}));
		
		final var runThrowSomethingAsIfItDoesntThrowAnything = async.def("runThrowSomethingAsIfItDoesntThrowAnything", await -> {
			await.apply(throwSomething.get());
		});
		
		final var main2 = async.def(await -> {
			System.out.println("Error handling test...");
			try {
				await.apply(runThrowSomethingAsIfItDoesntThrowAnything.get());
			}
			catch(UncheckedWrapper uw) {
				System.err.println(uw.toString());
				var e = uw.getOriginal();
				if (e instanceof IndexOutOfBoundsException) {
					System.out.println("If this text is displayed. Error handing is confused...");
				}
				else if (e instanceof NullPointerException) {
					System.out.println("If this text is displayed. Error handling works.");
				}
				else {
					throw uw;
				}
			}
		});
		
		
		
		main2.get();
		async.execute();
		
		
		// A big mess that I call example 3:
		// ---------------------------------------------------
		final var get8 = async.def("get8", await -> {
			await.sleep(1000);
			return 8;
		});
		
		final var get2plus8 = async.def(await -> {
			var Promise8 = get8.get();
			return await.apply(Promise8) + 2;
		});
		
		final var getHello10 = async.def("getHello10", await -> {
			var PromiseForHello = getHello.get();
			var PromiseFor10 = get2plus8.get();
			try {
				return await.apply(PromiseForHello) + await.apply(PromiseFor10);
			}
			catch(UncheckedWrapper ae) {
				var e = ae.getOriginal();
				if (e instanceof InterruptedException) {
					System.out.println("SOMETHING WENT WRONG! interrupted");
				}
				else if (e instanceof IndexOutOfBoundsException) {
					System.out.println("Index out of bounds??");
				}
				return "SOMETHINGWENTWRONG";
			}
		});
		
		final var getHelloAnd = async.def((Async.Await await, Double addition) -> {
			return await.apply(getHello.get()) + addition;
		});
		
		
		getHello10.get().thenAccept(r -> System.out.println(r));
		getHelloAnd.apply(42.41).then(r -> {System.out.println(r);});
		
		
		final var slowAdd = async.def("slowAdd", (Async.Await await , Double d1, Double d2) -> {
			await.sleep(1000);
			return d1 + d2;
		});
		
		@SuppressWarnings("unused")
		final var slowAddSpecific = async.<Double, Double, Long, Double>def((await, d1, d2, waitTime) -> {
			await.sleep(waitTime);
			return d1 + d2;
		});
		
		
		var slowPromise = slowAdd.apply(0.1, 0.2);
		
		// multiple execution threads? Why not!
		new Thread(() -> { async.execute(); }, "execution thread 1").start();
		new Thread(() -> { async.execute(); }, "execution thread 2").start();
		new Thread(() -> { async.execute(); }, "execution thread 3").start();
		
		// awaiting promise instead of calling them. unlike javascript, java can block.
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve"); } catch(Throwable e) {} }, "steve").start();
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve2"); } catch(Throwable e) {} }, "steve2").start();
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve3"); } catch(Throwable e) {} }, "steve3").start();
		try{System.out.println(slowPromise.await());}catch(Throwable e) {};
		
		
		
		//CoThread run test
		CoThread<Integer> numberGiver = new CoThread<>((yield) -> {
			try {
				for(int i = 1; i <= 5; ++i) {
					Thread.sleep(500);
					yield.accept(i);
				}
			}
			catch (InterruptedException e) {}
		}, "number giver");
		numberGiver.start();
		
		final var numberGetter = async.def((await) -> {
			try {
				for(;;) {
					final var numPromise = numberGiver.run();
					
//					numPromise.then(n -> {return n + 1;}).then(n -> {return n / 0;}).then(n -> {System.out.println(n);}).onCatch(e -> {System.err.println(e);});
//					numPromise.then(n -> {System.out.println(n / 2);});
					
					final var num = await.apply(numPromise);
					
					System.out.println(num);
				}
			}
			catch (UncheckedWrapper uw) {
				try { throw uw.getOriginal(); }
				catch (CoThreadCompleteException ctce){
					System.out.println("CoThread is out of numbers");
				}
				catch (Throwable e) {
					throw uw;
				}
			}
		});
		
		
		numberGetter.get();
		
		async.execute();
		numberGiver.close();
	}
}
