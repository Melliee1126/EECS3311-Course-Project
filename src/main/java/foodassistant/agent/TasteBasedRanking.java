package foodassistant.agent;

import foodassistant.model.Recommendation;
import foodassistant.model.Restaurant;
import foodassistant.model.UserPreferences;

import java.util.ArrayList;
import java.util.List;

/** Prefers places that match the account's interests, likes, and usual budget. */
public final class TasteBasedRanking implements RankingStrategy {
    @Override
    public List<Recommendation> rank(List<Restaurant> restaurants, RankContext context) {
        List<Recommendation> ranked = new ArrayList<>();
        UserPreferences preferences = context.preferences();
        for (Restaurant restaurant : restaurants) {
            double score = restaurant.getRating();
            List<String> reasons = new ArrayList<>();
            for (String interest : preferences.getInterests()) {
                if (listed(preferences.getDislikes(), interest)) {
                    continue;
                }
                if (RestaurantMatch.matches(restaurant, interest)) {
                    score += 4;
                    reasons.add("Matches your interest in " + interest);
                }
            }
            for (String like : preferences.getLikes()) {
                if (RestaurantMatch.matches(restaurant, like) && !mentions(reasons, like)) {
                    int boost = tilt(preferences.strength(like), 2);
                    score += boost;
                    reasons.add(boost >= 3
                            ? "A strong match for " + like
                            : boost <= 1 ? "A mild match for " + like : "Fits " + like);
                }
            }
            for (String dislike : preferences.getDislikes()) {
                if (RestaurantMatch.matches(restaurant, dislike)) {
                    int penalty = tilt(preferences.strength(dislike), 3);
                    score -= penalty;
                    reasons.add(penalty >= 3
                            ? "You feel strongly against " + dislike
                            : "Similar to " + dislike + ", which you tend to skip");
                }
            }
            String comfort = preferences.getPriceComfort();
            String price = restaurant.getPriceRange();
            if (!comfort.isBlank() && !price.isBlank() && price.length() <= comfort.length()) {
                score += 1.5;
                reasons.add("Within your usual " + comfort + " budget");
            }
            double kilometers = RestaurantMatch.kilometers(restaurant, context.latitude(), context.longitude());
            if (kilometers >= 0) {
                score += Math.max(0, 2.5 - kilometers / 8.0);
                if (kilometers < 3) {
                    reasons.add("Close to you");
                }
            }
            if (reasons.isEmpty()) {
                reasons.add(restaurant.getRating() >= 4 ? "Highly rated nearby" : "Nearby option");
            }
            ranked.add(new Recommendation(restaurant, "", score, join(reasons), cap(reasons)));
        }
        ranked.sort(TasteBasedRanking::byScore);
        return ranked;
    }

    private static int byScore(Recommendation left, Recommendation right) {
        int score = Double.compare(right.getScore(), left.getScore());
        if (score != 0) {
            return score;
        }
        return left.getRestaurant().getName().compareToIgnoreCase(right.getRestaurant().getName());
    }

    private static int tilt(int strength, int fallback) {
        if (strength == 0) {
            return fallback;
        }
        return Math.min(6, Math.max(1, Math.abs(strength)));
    }

    private static boolean listed(List<String> values, String word) {
        for (String value : values) {
            if (value.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentions(List<String> reasons, String word) {
        for (String reason : reasons) {
            if (reason.toLowerCase(java.util.Locale.ROOT).contains(word.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> cap(List<String> reasons) {
        return reasons.size() <= 3 ? reasons : List.copyOf(reasons.subList(0, 3));
    }

    private static String join(List<String> reasons) {
        return String.join(". ", cap(reasons));
    }
}
