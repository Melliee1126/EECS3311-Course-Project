package foodassistant.model;

import java.util.List;

/** One ranked restaurant and why it was chosen. */
public final class Recommendation {
    private final Restaurant restaurant;
    private final String photo;
    private final double score;
    private final String explanation;
    private final List<String> matchReasons;

    public Recommendation(Restaurant restaurant, String photo, double score, String explanation, List<String> matchReasons) {
        this.restaurant = restaurant;
        this.photo = photo == null ? "" : photo;
        this.score = score;
        this.explanation = explanation == null ? "" : explanation;
        this.matchReasons = List.copyOf(matchReasons == null ? List.of() : matchReasons);
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public String getPhoto() {
        return photo;
    }

    public double getScore() {
        return score;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<String> getMatchReasons() {
        return matchReasons;
    }

    public Recommendation withPhoto(String photo) {
        return new Recommendation(restaurant, photo, score, explanation, matchReasons);
    }
}
