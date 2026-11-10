package foodassistant.feedback;

import foodassistant.model.Review;

/** Hears that a review or a saved place changed. */
public interface FeedbackObserver {
    void onFeedback(String accountId, Review review);
}
