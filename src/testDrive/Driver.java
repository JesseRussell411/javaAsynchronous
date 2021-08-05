package testDrive;
import java.util.Iterator;

import asynchronous.*;
import asynchronous.asyncAwait.Async;

public class Driver {
	public static void main(String[] args) throws InterruptedException{
		
		var getHello = new Async<String>(await -> {
			return await.apply(new Promise<String>(resolve -> new Thread(() -> {
				try {Thread.sleep(4000);} catch(InterruptedException e) {}
				resolve.accept("hello");
			}, "getHello").start()));
		}, "getHello");
		
		var get8 = new Async<Integer>(await -> {
			int num = await.apply(new Promise<Integer>(resolve -> new Thread(() -> {
					try {
						Thread.sleep(1000);
					}
					catch(InterruptedException e) {}
					
					resolve.accept(8);
				}, "get8").start()));
			
			return num;
		}, "get8");
		
		var add2 = new Async<Integer>(await -> {
			var Promise8 = get8.get();
			return await.apply(Promise8) + 2;
		}, "add2");
		
		var getHello10 = new Async<String>(await -> {
			var PromiseForHello = getHello.get();
			var PromiseFor10 = add2.get();
			return await.apply(PromiseForHello) + await.apply(PromiseFor10); 
		}, "getHello10");
		
		
		getHello10.get().then(r -> {System.out.println(r);});
		
		Async.execute();
	}
}
