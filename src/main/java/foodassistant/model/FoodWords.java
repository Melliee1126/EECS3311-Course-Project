package foodassistant.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Food words the assistant and the taste profile both recognize. */
public final class FoodWords {
    private static final List<String> WORDS = List.of(
            "Bakery", "BBQ", "Banh mi", "Breakfast", "Brunch", "Burgers", "Burritos", "Cafe", "Canadian",
            "Chinese", "Coffee", "Comfort food", "Curry", "Dessert", "Dim sum", "Dumplings", "Falafel",
            "French", "Fried chicken", "Gluten-free", "Greek", "Halal", "Hot pot", "Ice cream", "Indian",
            "Italian", "Japanese", "Kebab", "Korean", "Mediterranean", "Mexican", "Noodles", "Pad thai",
            "Pasta", "Pho", "Pizza", "Poke", "Ramen", "Salad", "Sandwiches", "Sashimi", "Seafood",
            "Shawarma", "Soba", "Soup", "Spanish", "Spicy", "Steak", "Sushi", "Tacos", "Tapas", "Tea",
            "Tempura", "Thai", "Udon", "Vegan", "Vegetarian", "Vietnamese", "Wings",
            "Beef", "Dairy-free", "Egg", "Fish", "Kosher", "Lamb", "Nut-free", "Peanut", "Pescatarian",
            "Pork", "Sesame", "Shellfish", "Soy", "Tree nut",
            "Aromatic", "Bitter", "Buttery", "Charred", "Cheesy", "Chewy", "Chili", "Citrusy", "Cold",
            "Creamy", "Crispy", "Crunchy", "Earthy", "Fermented", "Fiery", "Flaky", "Floral", "Fresh",
            "Fragrant", "Funky", "Garlicky", "Greasy", "Healthy", "Hearty", "Herby", "Hot", "Hot and sour",
            "Indulgent", "Juicy", "Light", "Mild", "Numbing", "Nutty", "Peppery", "Pickled", "Refreshing",
            "Rich", "Salty", "Savory", "Silky", "Smoky", "Sour", "Sweet", "Sweet and sour", "Tangy", "Tart",
            "Tender", "Umami", "Zesty");

    private static final List<String> DIETS = List.of(
            "Vegetarian", "Vegan", "Pescatarian", "Halal", "Kosher", "Gluten-free", "Dairy-free", "Nut-free");

    private static final List<String> RESTRICTIONS = List.of(
            "Peanut", "Tree nut", "Shellfish", "Soy", "Egg", "Sesame", "Pork", "Beef", "Lamb", "Fish");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("savoury", "Savory"),
            Map.entry("chilli", "Chili"),
            Map.entry("chile", "Chili"),
            Map.entry("sugary", "Sweet"),
            Map.entry("acidic", "Tangy"),
            Map.entry("mala", "Numbing"),
            Map.entry("shrimp", "Shellfish"),
            Map.entry("prawn", "Shellfish"),
            Map.entry("soya", "Soy"));

    private static final List<Term> TERMS = buildTerms();

    /** A surface spelling and the taste word it counts as. */
    public record Term(String form, String canonical) {
    }

    private FoodWords() {
    }

    public static List<String> words() {
        return WORDS;
    }

    public static boolean isDiet(String word) {
        return listed(DIETS, word);
    }

    public static boolean isRestriction(String word) {
        return listed(RESTRICTIONS, word);
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

    public static List<Term> terms() {
        return TERMS;
    }

    public static String canonical(String word) {
        String normal = normalize(word);
        if (normal.isEmpty()) {
            return "";
        }
        for (Term term : TERMS) {
            if (term.form.equals(normal)) {
                return term.canonical;
            }
        }
        return "";
    }

    public static List<String> mentioned(String text) {
        String padded = " " + normalize(text) + " ";
        boolean[] taken = new boolean[padded.length()];
        List<String> found = new ArrayList<>();
        for (Term term : TERMS) {
            String needle = " " + term.form + " ";
            int from = 0;
            while (from < padded.length()) {
                int at = padded.indexOf(needle, from);
                if (at < 0) {
                    break;
                }
                int start = at + 1;
                int end = at + needle.length() - 1;
                if (!overlaps(taken, start, end) && !found.contains(term.canonical)) {
                    mark(taken, start, end);
                    found.add(term.canonical);
                }
                from = at + term.form.length();
            }
        }
        return found;
    }

    private static List<Term> buildTerms() {
        List<Term> terms = new ArrayList<>();
        for (String word : WORDS) {
            addForm(terms, normalize(word), word);
        }
        for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
            addForm(terms, alias.getKey(), alias.getValue());
        }
        terms.sort((left, right) -> Integer.compare(right.form.length(), left.form.length()));
        return List.copyOf(terms);
    }

    private static void addForm(List<Term> terms, String form, String canonical) {
        if (form.isBlank() || canonical.isBlank()) {
            return;
        }
        terms.add(new Term(form, canonical));
        if (form.endsWith("s") && form.length() > 3 && !form.endsWith("ss")) {
            terms.add(new Term(form.substring(0, form.length() - 1), canonical));
        } else if (!form.endsWith("s")) {
            terms.add(new Term(form + "s", canonical));
        }
    }

    private static boolean listed(List<String> words, String word) {
        String canonical = canonical(word);
        String key = canonical.isEmpty() ? word == null ? "" : word.trim() : canonical;
        for (String item : words) {
            if (item.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(boolean[] used, int start, int end) {
        for (int index = start; index < end && index < used.length; index++) {
            if (used[index]) {
                return true;
            }
        }
        return false;
    }

    private static void mark(boolean[] used, int start, int end) {
        for (int index = start; index < end && index < used.length; index++) {
            used[index] = true;
        }
    }
}
