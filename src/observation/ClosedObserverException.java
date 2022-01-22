package observation;

public class ClosedObserverException extends RuntimeException {
    private final Observer<?> observer;

    public Observer<?> getObserver() {
        return observer;
    }

    public ClosedObserverException(Observer<?> observer) {
        super("An attempt was made to give a reaction to a closed observer.");
        this.observer = observer;
    }
}
