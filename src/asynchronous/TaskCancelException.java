package asynchronous;

import java.util.concurrent.CancellationException;

public class TaskCancelException extends CancellationException{
	private static final long serialVersionUID = 1L;
	private Task<?> task;
	public Task<?> getTask() { return task; }
	public TaskCancelException(Task<?> task) {
		super();
		this.task = task;
	}

}
