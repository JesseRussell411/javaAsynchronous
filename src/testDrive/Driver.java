package testDrive;
import java.util.Random;
import java.util.function.*;

import asynchronous.*;
import asynchronous.asyncAwait.*;

public class Driver {
	public static void main(String[] args) throws InterruptedException{
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
		// final var fetchStuffFromServer = new Async1<Integer, Responce>((await, id) -> {
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
		final var getHello = new Async<String>(await -> {
			// sleep for a bit
			await.sleep(1000);
			
			// get around to returning hello
			return "hello";
		}, "getHello");
		
		final var getSpace = new Async<String>(await -> {
			//sleep for a bit
			await.sleep(500);
			
			// get around to returning a space
			return " ";
		}, "getSpace");
		
		final var getWorld = new Async<String>(await -> {		
			// sleep for a bit
			await.sleep(900);
			
			//get around to returning world
			return "world";
		}, "getWorld");
		
		final var getHelloworld = new Async<String>(await -> {
			// get the promises from each function
			final var hello = getHello.get();
			final var space = getSpace.get();
			final var world = getWorld.get();
			
			// await those promises
			return await.apply(hello) +
					await.apply(space) +
					await.apply(world);
		}, "getHelloworld");
		
		final var main = new AsyncVoid(await -> {
			System.out.println("Getting hello world...");
			System.out.println(await.apply(getHelloworld.get()));
			System.out.println("Hello world has been gotten.");
		}, "main");
		
		
		System.out.println("Hello world example: ");
		main.get();
		// execute doesn't HAVE to be called at the end of main. It can really be called anywhere. But beware, it blocks until all async function calls are complete.
		Async.execute();
		
		
		
		// Error handling example/test:
		// ---------------------------
		
		// Errors propagate up just like in javascript, but they do have to be wrapped in AsyncException (a RuntimeException)
		// because checked exceptions get really annoying when you're using lambdas.
		final var throwSomething = new AsyncVoid(await -> {
			throw new NullPointerException("This pointer doesn't exist. Oh, wait that means void! sorry");
		}, "throwSomething");
		
		final var runThrowSomethingAsIfItDoesntThrowAnything = new AsyncVoid(await -> {
			await.apply(throwSomething.get());
		}, "runThrowSomethingAsIfItDoesntThrowAnything");
		
		final var main2 = new AsyncVoid(await -> {
			
			System.out.println("Error handling test...");
			try {
				await.apply(runThrowSomethingAsIfItDoesntThrowAnything.get());
			}
			catch(AsyncException ae) {
				var e = ae.getOriginal();
				if (e instanceof IndexOutOfBoundsException) {
					System.out.println("If this text is displayed. Error handing is confused...");
				}
				if (e instanceof NullPointerException) {
					System.out.println("If this text is displayed. Error handling works.");
				}
			}
			
		});
		
		main2.get();
		Async.execute();
		
		
		// A big mess that I call example 3:
		// ---------------------------------------------------
		final var get8 = new Async<Integer>(await -> {
			await.sleep(1000);
			return 8;
		}, "get8");
		
		final var get2plus8 = new Async<Integer>(await -> {
			var Promise8 = get8.get();
			return await.apply(Promise8) + 2;
		}, "add2");
		
		final var getHello10 = new Async<String>(await -> {
			var PromiseForHello = getHello.get();
			var PromiseFor10 = get2plus8.get();
			try {
				return await.apply(PromiseForHello) + await.apply(PromiseFor10);
			}
			catch(AsyncException ae) {
				var e = ae.getOriginal();
				if (e instanceof InterruptedException) {
					System.out.println("SOMETHING WENT WRONG! interrupted");
				}
				else if (e instanceof IndexOutOfBoundsException) {
					System.out.println("Index out of bounds??");
				}
				return "SOMETHINGWENTWRONG";
			}
		}, "getHello10");
		
		final var getHelloAnd = new Async1<Double, String>((await, addition) -> {
			return await.apply(getHello.get()) + addition;
		});
		
		
		getHello10.get().then(r -> {System.out.println(r);});
		getHelloAnd.apply(42.41).then(r -> {System.out.println(r);});
		
		
		final var slowAdd = new Async2<Double, Double, Double>((await, d1, d2) -> {
			await.sleep(1000);
			return d1 + d2;
		}, "slowAdd");
		
		final var slowAddSpecific = new Async3<Double, Double, Long, Double>((await, d1, d2, waitTime) -> {
			await.sleep(waitTime);
			return d1 + d2;
		}, "slowAddSpecific");
		
		
		var slowPromise = slowAdd.apply(0.1, 0.2);
		
		// multiple execution threads? Why not!
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 1").start();
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 2").start();
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 3").start();
		
		// awaiting promise instead of calling them. unlike javascript, java can block.
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve"); } catch(InterruptedException e) {} }, "steve").start();
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve2"); } catch(InterruptedException e) {} }, "steve2").start();
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve3"); } catch(InterruptedException e) {} }, "steve3").start();
		System.out.println(slowPromise.await());
		System.out.println(slowPromise.await());
		
		
		// lets try something different
		var rand = new Random();
		new Thread(() -> {
			try {
				while(true) {
					System.out.println(slowAddSpecific.apply(0.1, 0.2, (long)rand.nextInt(5000)).await());
				}
			}
			catch(InterruptedException e) {}
		}, "supplier 1").start();
		
		new Thread(() -> {
			try {
				while(true) {
					System.out.println(slowAddSpecific.apply(0.1, 0.6, (long)rand.nextInt(5000)).await());
				}
			}
			catch(InterruptedException e) {}
		}, "supplier 2").start();
		
		new Thread(() -> {
			try {
				while(true) {
					System.out.println(slowAddSpecific.apply(6.1, 0.6, (long)rand.nextInt(5000)).await());
				}
			}
			catch(InterruptedException e) {}
		}, "supplier 3").start();
		
		new Thread(() -> { try { while(true) { Async.execute();} } catch(InterruptedException e) {} }, "execution loop 1").start();
		new Thread(() -> { try { while(true) { Async.execute();} } catch(InterruptedException e) {} }, "execution loop 2").start();
		new Thread(() -> { try { while(true) { Async.execute();} } catch(InterruptedException e) {} }, "execution loop 3").start();
		new Thread(() -> { try { while(true) { Async.execute();} } catch(InterruptedException e) {} }, "execution loop 4").start();
	}
}
