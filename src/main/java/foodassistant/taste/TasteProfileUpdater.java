package foodassistant.taste;

import foodassistant.feedback.FeedbackObserver;
import foodassistant.model.FoodWords;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.model.UserPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rebuilds likes and dislikes after a review or a saved place changes. */
public final class TasteProfileUpdater implements FeedbackObserver {
    private final MemoryService memory;

    public TasteProfileUpdater(MemoryService memory) {
        this.memory = memory;
    }

    @Override
    public void onFeedback(String accountId, Review review) {
        memory.rebuild(accountId);
    }

    public static UserPreferences learn(UserPreferences current, List<Review> reviews, List<Restaurant> saved) {
        Map<String, Integer> liked = new LinkedHashMap<>();
        Map<String, Integer> disliked = new LinkedHashMap<>();
        if (reviews != null) {
            for (Review review : reviews) {
                Map<String, Integer> bucket = review.getRating() >= 4 ? liked : review.getRating() <= 2 ? disliked : null;
                if (bucket == null) {
                    continue;
                }
                addWords(bucket, review.getTags(), 1);
                addWords(bucket, FoodWords.mentioned(review.getBody() + " " + review.getRestaurantName()), 2);
            }
        }
        addWords(liked, foods(saved), 1);
        List<String> likes = top(liked, current.getInterests(), List.of());
        List<String> dislikes = top(disliked, current.getInterests(), likes);
        String price = commonPrice(saved);
        if (price.isEmpty()) {
            price = current.getPriceComfort();
        }
        return current.withLearned(likes, dislikes, price);
    }

    public static UserPreferences overlay(UserPreferences current, List<Restaurant> saved) {
        List<String> likes = new ArrayList<>(current.getLikes());
        for (String word : foods(saved)) {
            if (!has(likes, word) && !has(current.getDislikes(), word) && !has(current.getInterests(), word)) {
                likes.add(word);
            }
        }
        String price = current.getPriceComfort().isEmpty() ? commonPrice(saved) : current.getPriceComfort();
        return current.withLearned(likes, current.getDislikes(), price);
    }

    private static void addWords(Map<String, Integer> counts, List<String> words, int weight) {
        for (String word : words) {
            String canonical = FoodWords.canonical(word);
            String kept = canonical.isEmpty() ? word.trim() : canonical;
            if (kept.isEmpty() || kept.length() > 24) {
                continue;
            }
            counts.merge(kept, weight, Integer::sum);
        }
    }

    private static List<String> foods(List<Restaurant> saved) {
        List<String> words = new ArrayList<>();
        if (saved == null) {
            return words;
        }
        for (Restaurant restaurant : saved) {
            words.addAll(FoodWords.mentioned(restaurant.getName() + " " + restaurant.getCuisine() + " " + String.join(" ", restaurant.getTags())));
        }
        return words;
    }

    private static List<String> top(Map<String, Integer> counts, List<String> blocked, List<String> alsoBlocked) {
        List<String> ordered = new ArrayList<>(counts.keySet());
        ordered.sort((left, right) -> {
            int byCount = Integer.compare(counts.get(right), counts.get(left));
            return byCount != 0 ? byCount : left.compareToIgnoreCase(right);
        });
        List<String> chosen = new ArrayList<>();
        for (String word : ordered) {
            if (has(blocked, word) || has(alsoBlocked, word)) {
                continue;
            }
            chosen.add(word);
        }
        return chosen;
    }

    private static String commonPrice(List<Restaurant> saved) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (saved != null) {
            for (Restaurant restaurant : saved) {
                String price = UserPreferences.price(restaurant.getPriceRange());
                if (!price.isEmpty()) {
                    counts.merge(price, 1, Integer::sum);
                }
            }
        }
        String best = "";
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private static boolean has(List<String> values, String word) {
        for (String value : values) {
            if (value.equalsIgnoreCase(word) || FoodWords.normalize(value).equals(FoodWords.normalize(word))) {
                return true;
            }
        }
        return false;
    }
}
