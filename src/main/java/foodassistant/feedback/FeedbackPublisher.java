package foodassistant.feedback;

import foodassistant.model.Review;

import java.util.ArrayList;
import java.util.List;

/** Tells taste and history listeners that the account did something. */
public final class FeedbackPublisher {
    private final List<FeedbackObserver> observers = new ArrayList<>();

    public void subscribe(FeedbackObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    public void unsubscribe(FeedbackObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String accountId, Review review) {
        for (FeedbackObserver observer : List.copyOf(observers)) {
            try {
                observer.onFeedback(accountId, review);
            } catch (RuntimeException exception) {
                String message = exception.getMessage();
                System.err.println(message == null || message.isBlank() ? "Taste profile was not updated." : message);
            }
        }
    }
}
