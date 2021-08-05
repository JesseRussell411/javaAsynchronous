package testDrive;
import java.util.Iterator;

import asynchronous.*;
import asynchronous.asyncAwait.Async;

public class Driver {
	public static void main(String[] args) throws InterruptedException{
		Async<Integer> async = new Async<>(await -> {
			int num = await.apply(new Promise<Integer>(resolve -> 
				new Thread(() -> {
					try {
						Thread.sleep(900);
					}
					catch(InterruptedException e) {}
					resolve.accept(8);
				}).start()
			));
			return num;
		});
		var prom = async.call();
		prom.then(r -> {System.out.println(r);});
		
		Async.execute();
	}
}
