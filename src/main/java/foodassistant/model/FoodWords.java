package foodassistant.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Food words the assistant and the taste profile both recognize. */
public final class FoodWords {
    private static final List<String> WORDS = List.of(
            "Bakery", "BBQ", "Banh mi", "Breakfast", "Brunch", "Burgers", "Burritos", "Cafe", "Canadian",
            "Chinese", "Coffee", "Comfort food", "Curry", "Dessert", "Dim sum", "Dumplings", "Falafel",
            "French", "Fried chicken", "Gluten-free", "Greek", "Halal", "Hot pot", "Ice cream", "Indian",
            "Italian", "Japanese", "Kebab", "Korean", "Mediterranean", "Mexican", "Noodles", "Pad thai",
            "Pasta", "Pho", "Pizza", "Poke", "Ramen", "Salad", "Sandwiches", "Sashimi", "Seafood",
            "Shawarma", "Soba", "Soup", "Spanish", "Spicy", "Steak", "Sushi", "Tacos", "Tapas", "Tea",
            "Tempura", "Thai", "Udon", "Vegan", "Vegetarian", "Vietnamese", "Wings");

    private FoodWords() {
    }

    public static List<String> words() {
        return WORDS;
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    public static boolean containsWord(String haystack, String word) {
        String needle = normalize(word);
        if (needle.isEmpty()) {
            return false;
        }
        return (" " + normalize(haystack) + " ").contains(" " + needle + " ");
    }

    public static String canonical(String word) {
        String normal = normalize(word);
        if (normal.isEmpty()) {
            return "";
        }
        for (String known : WORDS) {
            if (normalize(known).equals(normal)) {
                return known;
            }
        }
        return "";
    }

    public static List<String> mentioned(String text) {
        List<String> found = new ArrayList<>();
        List<String> ordered = new ArrayList<>(WORDS);
        ordered.sort((left, right) -> Integer.compare(right.length(), left.length()));
        for (String word : ordered) {
            if (containsWord(text, word)) {
                found.add(word);
            }
        }
        return found;
    }
}
