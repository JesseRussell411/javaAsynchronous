# javaAsynchronous
Experimenting with abusing java to add async functionality

Firsty, this is just a side project. I don't think this should seriously be used for asynchronous promgramming. There are other, more seriouse/professional libraries for that, like EA Async which actually adds real Asyncronouse functionality to the JVM.

This library doesn't add anything to the JVM. It cheats! Java does not support coroutines, so threads are used to fake it. Additionaly, instead of using the built in Futures of java (like what would make sense), I have made my own Promise class because I didn't want to learn how to use futures. I'm a web developer by trade so naturally I think javascript has the best implementation of promises. Therefore, the Promise class behaves as much like javascript's promise class as possible with an initializer passed to the constructor with resolve and reject function as well as then and error methods for giving it callbacks to run when it completes.
