package testDrive;
import java.util.Iterator;
import java.util.Random;

import asynchronous.*;
import asynchronous.asyncAwait.*;

public class Driver {
	public static void main(String[] args) throws InterruptedException{
		
		final var getHello = new Async<String>(await -> {
			return await.apply(new Promise<String>(resolve -> new Thread(() -> {
				try {Thread.sleep(4000);} catch(InterruptedException e) {}
				resolve.accept("hello");
			}, "getHello").start()));
		}, "getHello");
		
		final var get8 = new Async<Integer>(await -> {
			int num = await.apply(new Promise<Integer>(resolve -> new Thread(() -> {
					try {
						Thread.sleep(1000);
					}
					catch(InterruptedException e) {}
					
					resolve.accept(8);
				}, "get8").start()));
			
			return num;
		}, "get8");
		
		final var add2 = new Async<Integer>(await -> {
			var Promise8 = get8.get();
			return await.apply(Promise8) + 2;
		}, "add2");
		
		final var getHello10 = new Async<String>(await -> {
			var PromiseForHello = getHello.get();
			var PromiseFor10 = add2.get();
			return await.apply(PromiseForHello) + await.apply(PromiseFor10); 
		}, "getHello10");
		
		final var getHelloAnd = new Async1<Double, String>((await, addition) -> {
			return await.apply(getHello.get()) + addition;
		});
		
		
		getHello10.get().then(r -> {System.out.println(r);});
		getHelloAnd.apply(42.41).then(r -> {System.out.println(r);});
		
		
		final var slowAdd = new Async2<Double, Double, Double>((await, d1, d2) -> {
			return await.apply(new Promise<Double>(resolve -> new Thread(() -> {
				try {
					Thread.sleep(7000);
					resolve.accept(d1 + d2);
				}
				catch (InterruptedException e) {}
			}).start()));
		}, "slowAdd");
		
		final var slowAddSpecific = new Async3<Double, Double, Long, Double>((await, d1, d2, waitTime) -> {
			return await.apply(new Promise<Double>(resolve -> new Thread(() -> {
				try {
					Thread.sleep(waitTime);
					resolve.accept(d1 + d2);
				}
				catch (InterruptedException e) {}
			}).start()));
		}, "slowAddSpecific");
		
		
		var slowPromise = slowAdd.apply(0.1, 0.2);
		
		// multiple execution threads? Why not!
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 1").start();
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 2").start();
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 3").start();
		
		// awaiting promise instead of calling then. unlike javascript, java can block.
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve"); } catch(InterruptedException e) {} }, "steve").start();
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve2"); } catch(InterruptedException e) {} }, "steve2").start();
		new Thread(() -> { try { System.out.println(slowPromise.await() + "from steve3"); } catch(InterruptedException e) {} }, "steve3").start();
		System.out.println(slowPromise.await());
		System.out.println(slowPromise.await());
		
		var rand = new Random();
		
		// lets try something different
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
		
		new Thread(() -> { try { while(true) {Async.execute();} } catch(InterruptedException e) {} }, "execution loop 1").start();
	}
}
