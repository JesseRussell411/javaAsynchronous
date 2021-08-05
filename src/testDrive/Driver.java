package testDrive;
import java.util.Iterator;

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
		getHelloAnd.get(42.41).then(r -> {System.out.println(r);});
		
		
		final var slowAdd = new Async2<Double, Double, Double>((await, d1, d2) -> {
			return await.apply(new Promise<Double>(resolve -> new Thread(() -> {
				try {
					Thread.sleep(10000);
					resolve.accept(d1 + d2);
				}
				catch (InterruptedException e) {}
			}).start()));
		});
		
		
		slowAdd.get(0.1, 0.2).then(r -> {System.out.println(r);});
		
		// multiple execution thread? Why not!
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 1").start();
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 2").start();
		new Thread(() -> { try { Async.execute(); } catch(InterruptedException e) {} }, "execution thread 3").start();
	}
}
