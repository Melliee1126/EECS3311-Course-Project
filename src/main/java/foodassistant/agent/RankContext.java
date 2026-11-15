package foodassistant.agent;

import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.model.UserPreferences;

import java.util.ArrayList;
import java.util.List;

/** What a ranking strategy is allowed to look at. */
public final class RankContext {
    private final UserPreferences preferences;
    private final double latitude;
    private final double longitude;
    private final List<Review> reviews;
    private final List<Restaurant> saved;
    private final List<String> askedFoods;

    public RankContext(UserPreferences preferences, double latitude, double longitude) {
        this(preferences, latitude, longitude, List.of(), List.of(), List.of());
    }

    public RankContext(
            UserPreferences preferences,
            double latitude,
            double longitude,
            List<Review> reviews,
            List<Restaurant> saved,
            List<String> askedFoods) {
        this.preferences = preferences == null ? UserPreferences.empty() : preferences;
        this.latitude = latitude;
        this.longitude = longitude;
        this.reviews = reviews == null ? List.of() : List.copyOf(reviews);
        this.saved = saved == null ? List.of() : List.copyOf(saved);
        this.askedFoods = clean(askedFoods);
    }

    public UserPreferences preferences() {
        return preferences;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public List<Review> reviews() {
        return reviews;
    }

    public List<Restaurant> saved() {
        return saved;
    }

    public List<String> askedFoods() {
        return askedFoods;
    }

    private static List<String> clean(List<String> foods) {
        List<String> kept = new ArrayList<>();
        if (foods == null) {
            return List.of();
        }
        for (String food : foods) {
            if (food == null || food.isBlank()) {
                continue;
            }
            boolean seen = false;
            for (String existing : kept) {
                if (existing.equalsIgnoreCase(food)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                kept.add(food.trim());
            }
        }
        return List.copyOf(kept);
    }
}
