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
                if (RestaurantMatch.matches(restaurant, interest)) {
                    score += 4;
                    reasons.add("Matches your interest in " + interest);
                }
            }
            for (String like : preferences.getLikes()) {
                if (RestaurantMatch.matches(restaurant, like) && !mentions(reasons, like)) {
                    score += 2;
                    reasons.add("Fits " + like + " from your reviews");
                }
            }
            for (String dislike : preferences.getDislikes()) {
                if (RestaurantMatch.matches(restaurant, dislike)) {
                    score -= 3;
                    reasons.add("Similar to " + dislike + ", which you rated lower");
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
