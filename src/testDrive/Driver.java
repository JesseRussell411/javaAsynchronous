package testDrive;
import asyncronous.*;

public class Driver {
	public static void main(String [] args) throws InterruptedException, Exception{
		CoThread<Integer> co = new CoThread<Integer>(yield -> {
			yield.accept(0);
			yield.accept(1);
			yield.accept(2);
			yield.accept(3);
			yield.accept(4);
			yield.accept(5);
			yield.accept(6);
			yield.accept(7);
		});
		co.start();
		
		
		while(co.notComplete()) {
			System.out.println(co.await());
			Thread.sleep(1);
		}
		
		co.close();
	}
}
