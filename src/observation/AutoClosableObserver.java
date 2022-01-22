package observation;

public class AutoClosableObserver<T> extends Observer<T> implements AutoCloseable{
    public AutoClosableObserver(Observable<T> subject){
        super(subject);
    }

    @Override
    public void close(){
        // For reasons of simplicity the logic for closing is built into the Observer base class, but not used. It's just easier this way.
        closeSelf();
    }
}
