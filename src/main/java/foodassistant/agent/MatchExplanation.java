package foodassistant.agent;

import foodassistant.model.FoodWords;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.model.UserPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Why one place fits this account, using taste, reviews, and saved places before rating or distance. */
final class MatchExplanation {
    private MatchExplanation() {
    }

    static String write(Restaurant restaurant, RankContext context) {
        UserPreferences preferences = context.preferences();
        List<String> sentences = new ArrayList<>();
        String history = history(restaurant, context);
        if (!history.isEmpty()) {
            sentences.add(history);
        }
        List<String> likes = ranked(preferences.getLikes(), restaurant, preferences);
        List<String> interests = ranked(preferences.getInterests(), restaurant, preferences);
        if (!likes.isEmpty()) {
            sentences.add(likeSentence(likes.get(0), preferences, restaurant));
        }
        if (sentences.size() < 2 && likes.size() > 1) {
            sentences.add(likeSentence(likes.get(1), preferences, restaurant));
        }
        if (sentences.size() < 2) {
            for (String interest : interests) {
                if (!listed(likes, interest)) {
                    sentences.add(interestSentence(interest, restaurant));
                    break;
                }
            }
        }
        if (sentences.size() < 2) {
            for (String asked : context.askedFoods()) {
                if (RestaurantMatch.matches(restaurant, asked) && !listed(likes, asked) && !listed(interests, asked)) {
                    sentences.add("You asked for " + lower(asked) + ", and " + because(restaurant, asked) + ".");
                    break;
                }
            }
        }
        if (sentences.size() < 2) {
            for (String diet : preferences.diets()) {
                if (RestaurantMatch.matches(restaurant, diet) && !mentioned(sentences, diet)) {
                    sentences.add("You keep a " + lower(diet) + " diet, and " + because(restaurant, diet) + ".");
                    break;
                }
            }
        }
        if (sentences.size() < 2 && inBudget(preferences, restaurant)) {
            sentences.add("The price is inside the " + preferences.getPriceComfort() + " range you usually pick.");
        }
        if (sentences.isEmpty()) {
            return fallback(restaurant, preferences);
        }
        return String.join(" ", sentences.subList(0, Math.min(2, sentences.size())));
    }

    private static String history(Restaurant restaurant, RankContext context) {
        Review review = ownReview(restaurant, context.reviews());
        boolean saved = saved(restaurant, context.saved());
        if (review == null && !saved) {
            return "";
        }
        if (review == null) {
            return "You already saved this place.";
        }
        String stars = review.getRating() == 1 ? "1 star" : review.getRating() + " stars";
        String quote = quote(review.getBody());
        String opening = saved ? "You saved this and rated it " : "You rated this ";
        if (!quote.isEmpty()) {
            return opening + stars + ": \"" + quote + "\".";
        }
        if (review.getTags() != null && !review.getTags().isEmpty()) {
            return opening + stars + " and tagged it " + join(review.getTags()) + ".";
        }
        return opening + stars + ".";
    }

    private static String likeSentence(String word, UserPreferences preferences, Restaurant restaurant) {
        int stated = preferences.statedScore(word);
        int strength = preferences.strength(word);
        String food = lower(word);
        String because = because(restaurant, word);
        if (FoodWords.isDiet(word) && stated >= 8) {
            return "You keep a " + food + " diet, and " + because + ".";
        }
        if (stated >= 3) {
            return "You said you really like " + food + ", and " + because + ".";
        }
        if (stated == 1) {
            return "You said " + food + " is only a mild like, and " + because + ".";
        }
        if (stated > 0) {
            return "You said you like " + food + ", and " + because + ".";
        }
        if (strength >= 3) {
            return "Your reviews and saved places show a strong taste for " + food + ", and " + because + ".";
        }
        if (strength == 1) {
            return "You have a mild taste for " + food + ", and " + because + ".";
        }
        return "This fits your taste for " + food + ", and " + because + ".";
    }

    private static String interestSentence(String word, Restaurant restaurant) {
        return capitalize(word) + " is an interest you chose, and " + because(restaurant, word) + ".";
    }

    private static String because(Restaurant restaurant, String word) {
        if (FoodWords.containsWord(restaurant.getCuisine(), word)) {
            String cuisine = restaurant.getCuisine() == null ? "" : restaurant.getCuisine().trim();
            return "this is a " + cuisine.toLowerCase(Locale.ROOT) + " place";
        }
        if (FoodWords.containsWord(restaurant.getName(), word)) {
            return "the name lines up with that";
        }
        return "the tags include it";
    }

    private static String fallback(Restaurant restaurant, UserPreferences preferences) {
        String kind = restaurant.getCuisine() == null || restaurant.getCuisine().isBlank()
                ? "place"
                : restaurant.getCuisine().trim().toLowerCase(Locale.ROOT) + " place";
        String usual = "";
        if (!preferences.getLikes().isEmpty()) {
            usual = preferences.getLikes().get(0);
        } else if (!preferences.getInterests().isEmpty()) {
            usual = preferences.getInterests().get(0);
        }
        if (!usual.isEmpty()) {
            return "This " + kind + " is not one of your usual picks, such as " + lower(usual)
                    + ". It is here as a well-rated nearby option.";
        }
        if (restaurant.getRating() >= 4) {
            return "Your taste profile does not name this " + kind
                    + ", so this pick rests on its " + trimRating(restaurant.getRating()) + " rating and how close it is.";
        }
        return "Your taste profile does not name this " + kind + ". It is a nearby option for what you asked.";
    }

    private static List<String> ranked(List<String> words, Restaurant restaurant, UserPreferences preferences) {
        List<String> matched = new ArrayList<>();
        if (words == null) {
            return matched;
        }
        for (String word : words) {
            if (word != null && RestaurantMatch.matches(restaurant, word) && !listed(matched, word)) {
                matched.add(word);
            }
        }
        matched.sort((left, right) -> {
            int byWeight = Integer.compare(weight(preferences, right), weight(preferences, left));
            return byWeight != 0 ? byWeight : left.compareToIgnoreCase(right);
        });
        return matched;
    }

    private static int weight(UserPreferences preferences, String word) {
        return Math.max(Math.abs(preferences.statedScore(word)), Math.abs(preferences.strength(word)));
    }

    private static Review ownReview(Restaurant restaurant, List<Review> reviews) {
        if (reviews == null) {
            return null;
        }
        Review named = null;
        for (Review review : reviews) {
            if (review == null || review.getRating() <= 0) {
                continue;
            }
            if (sameId(review.getRestId(), restaurant.getRestId())) {
                return review;
            }
            if (named == null && sameName(review.getRestaurantName(), restaurant.getName())) {
                named = review;
            }
        }
        return named;
    }

    private static boolean saved(Restaurant restaurant, List<Restaurant> saved) {
        if (saved == null) {
            return false;
        }
        for (Restaurant place : saved) {
            if (place == null) {
                continue;
            }
            if (sameId(place.getRestId(), restaurant.getRestId()) || sameName(place.getName(), restaurant.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inBudget(UserPreferences preferences, Restaurant restaurant) {
        String comfort = preferences.getPriceComfort();
        String price = restaurant.getPriceRange();
        return comfort != null && !comfort.isBlank() && price != null && !price.isBlank() && price.length() <= comfort.length();
    }

    private static boolean sameId(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equals(right);
    }

    private static boolean sameName(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equalsIgnoreCase(right.trim());
    }

    private static boolean listed(List<String> values, String word) {
        for (String value : values) {
            if (value.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentioned(List<String> sentences, String word) {
        String needle = lower(word);
        for (String sentence : sentences) {
            if (sentence.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String quote(String body) {
        if (body == null) {
            return "";
        }
        String cleaned = body.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        if (cleaned.length() > 42) {
            cleaned = cleaned.substring(0, 42).trim() + "…";
        }
        return cleaned;
    }

    private static String join(List<String> tags) {
        int limit = Math.min(3, tags.size());
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            String tag = tags.get(i);
            if (tag != null && !tag.isBlank()) {
                shown.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (shown.isEmpty()) {
            return "a note";
        }
        if (shown.size() == 1) {
            return shown.get(0);
        }
        if (shown.size() == 2) {
            return shown.get(0) + " and " + shown.get(1);
        }
        return shown.get(0) + ", " + shown.get(1) + ", and " + shown.get(2);
    }

    private static String trimRating(double rating) {
        if (rating == Math.rint(rating)) {
            return String.valueOf((int) rating);
        }
        return String.format(Locale.ROOT, "%.1f", rating);
    }

    private static String lower(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    private static String capitalize(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
