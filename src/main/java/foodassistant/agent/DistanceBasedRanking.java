package foodassistant.agent;

import foodassistant.model.Recommendation;
import foodassistant.model.Restaurant;
import foodassistant.model.UserPreferences;

import java.util.ArrayList;
import java.util.List;

/** Prefers the closer restaurants, then uses taste only to break close calls. */
public final class DistanceBasedRanking implements RankingStrategy {
    @Override
    public List<Recommendation> rank(List<Restaurant> restaurants, RankContext context) {
        List<Recommendation> ranked = new ArrayList<>();
        UserPreferences preferences = context.preferences();
        for (Restaurant restaurant : restaurants) {
            double kilometers = RestaurantMatch.kilometers(restaurant, context.latitude(), context.longitude());
            double score = kilometers < 0 ? restaurant.getRating() : 40 - Math.min(40, kilometers);
            score += restaurant.getRating();
            List<String> reasons = new ArrayList<>();
            String distance = RestaurantMatch.distancePhrase(kilometers);
            if (!distance.isEmpty()) {
                reasons.add(distance);
            }
            for (String interest : preferences.getInterests()) {
                if (RestaurantMatch.matches(restaurant, interest)) {
                    score += 1;
                    reasons.add("Matches your interest in " + interest);
                    break;
                }
            }
            if (reasons.isEmpty()) {
                reasons.add("Nearby option");
            }
            ranked.add(new Recommendation(restaurant, "", score, String.join(". ", reasons), reasons));
        }
        ranked.sort((left, right) -> {
            int compared = Double.compare(right.getScore(), left.getScore());
            return compared != 0
                    ? compared
                    : left.getRestaurant().getName().compareToIgnoreCase(right.getRestaurant().getName());
        });
        return ranked;
    }
}
