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

/**
 * Rebuilds likes and dislikes from reviews, saved places, and tastes stated in chat.
 * A food's score is the sum of those signals. One positive review does not erase a stronger negative history.
 */
public final class TasteProfileUpdater implements FeedbackObserver {
    private final MemoryService memory;

    public TasteProfileUpdater(MemoryService memory) {
        this.memory = memory;
    }

    @Override
    public void onFeedback(String accountId, Review review) {
        memory.rebuild(accountId);
    }

    public static UserPreferences apply(
            UserPreferences current,
            List<ChatPreference.Signal> signals,
            List<String> cleared,
            List<Review> reviews,
            List<Restaurant> saved) {
        if (current == null) {
            current = UserPreferences.empty();
        }
        UserPreferences base = current.withoutStated(cleared);
        Map<String, Integer> delta = new LinkedHashMap<>();
        if (signals != null) {
            for (ChatPreference.Signal signal : signals) {
                if (signal.weight() == 0 || signal.food() == null || signal.food().isBlank()) {
                    continue;
                }
                if (listed(cleared, signal.food())) {
                    continue;
                }
                delta.merge(signal.food(), signal.weight(), Integer::sum);
            }
        }
        if (delta.isEmpty()) {
            return cleared == null || cleared.isEmpty() ? current : learn(base, reviews, saved);
        }
        return learn(base.plusStated(delta), reviews, saved);
    }

    public static UserPreferences learn(UserPreferences current, List<Review> reviews, List<Restaurant> saved) {
        if (current == null) {
            current = UserPreferences.empty();
        }
        Map<String, Integer> scores = new LinkedHashMap<>(current.getStated());
        if (reviews != null) {
            for (Review review : reviews) {
                int polarity = polarity(review.getRating());
                if (polarity == 0) {
                    continue;
                }
                addWords(scores, review.getTags(), polarity);
                addWords(scores, FoodWords.mentioned(review.getBody() + " " + review.getRestaurantName()), polarity * 2);
            }
        }
        addWords(scores, foods(saved), 1);
        for (String word : new ArrayList<>(scores.keySet())) {
            int statedScore = current.statedScore(word);
            if (FoodWords.isDiet(word) && statedScore >= 8) {
                scores.put(word, statedScore);
            } else if (FoodWords.isRestriction(word) && statedScore <= -4) {
                scores.put(word, statedScore);
            }
        }
        List<String> ordered = new ArrayList<>(scores.keySet());
        ordered.sort((left, right) -> {
            int byScore = Integer.compare(Math.abs(scores.get(right)), Math.abs(scores.get(left)));
            return byScore != 0 ? byScore : left.compareToIgnoreCase(right);
        });
        List<String> likes = new ArrayList<>();
        List<String> dislikes = new ArrayList<>();
        Map<String, Integer> affinity = new LinkedHashMap<>();
        for (String word : ordered) {
            int score = scores.get(word);
            boolean explicit = current.statedScore(word) != 0;
            if (score > 0 && (score >= 2 || explicit)) {
                if (has(current.getInterests(), word)) {
                    continue;
                }
                likes.add(word);
                affinity.put(word, score);
            } else if (score < 0 && (score <= -2 || explicit)) {
                dislikes.add(word);
                affinity.put(word, score);
            }
        }
        String price = commonPrice(saved);
        if (price.isEmpty()) {
            price = current.getPriceComfort();
        }
        return current.withLearned(likes, dislikes, price, affinity);
    }

    public static UserPreferences overlay(UserPreferences current, List<Restaurant> saved) {
        List<String> likes = new ArrayList<>(current.getLikes());
        for (String word : foods(saved)) {
            if (current.strength(word) < 0 || current.statedScore(word) < 0) {
                continue;
            }
            if (!has(likes, word) && !has(current.getDislikes(), word) && !has(current.getInterests(), word)) {
                likes.add(word);
            }
        }
        String price = current.getPriceComfort().isEmpty() ? commonPrice(saved) : current.getPriceComfort();
        return current.withLearned(likes, current.getDislikes(), price);
    }

    private static int polarity(int rating) {
        return switch (rating) {
            case 5 -> 3;
            case 4 -> 1;
            case 2 -> -1;
            case 1 -> -3;
            default -> 0;
        };
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

    private static boolean listed(List<String> values, String word) {
        if (values == null || word == null) {
            return false;
        }
        for (String value : values) {
            if (value.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
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
