package foodassistant.places;

import foodassistant.model.FoodWords;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Assigns category tags to a restaurant from its Google place types and its name.
 * The person does not type these tags.
 */
public final class PlaceTags {
    public static final int MAX = 6;

    private static final Set<String> SKIP = Set.of(
            "restaurant", "food", "point_of_interest", "establishment", "store",
            "premise", "political", "geocode", "plus_code", "street_address",
            "route", "locality", "neighborhood", "finance", "health", "lodging");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("hamburger_restaurant", "Burgers"),
            Map.entry("barbecue_restaurant", "BBQ"),
            Map.entry("barbeque_restaurant", "BBQ"),
            Map.entry("sandwich_shop", "Sandwiches"),
            Map.entry("meal_takeaway", "Takeout"),
            Map.entry("meal_delivery", "Delivery"),
            Map.entry("fast_food_restaurant", "Fast food"),
            Map.entry("fine_dining_restaurant", "Fine dining"),
            Map.entry("bar_and_grill", "Bar and grill"),
            Map.entry("wine_bar", "Wine"),
            Map.entry("irish_pub", "Pub"),
            Map.entry("pub", "Pub"),
            Map.entry("bar", "Bar"),
            Map.entry("cafe", "Cafe"),
            Map.entry("cat_cafe", "Cafe"),
            Map.entry("dog_cafe", "Cafe"),
            Map.entry("donut_shop", "Donuts"),
            Map.entry("bagel_shop", "Bagels"),
            Map.entry("juice_shop", "Juice"),
            Map.entry("acai_shop", "Acai"),
            Map.entry("chocolate_shop", "Dessert"),
            Map.entry("chocolate_factory", "Dessert"),
            Map.entry("confectionery", "Dessert"),
            Map.entry("candy_store", "Dessert"),
            Map.entry("deli", "Deli"),
            Map.entry("diner", "Diner"),
            Map.entry("buffet_restaurant", "Buffet"),
            Map.entry("cafeteria", "Cafeteria"),
            Map.entry("food_court", "Food court"));

    private static final Set<String> SENSORY = Set.of(
            "Aromatic", "Bitter", "Buttery", "Charred", "Cheesy", "Chewy", "Chili", "Citrusy", "Cold",
            "Creamy", "Crispy", "Crunchy", "Earthy", "Fermented", "Fiery", "Flaky", "Floral", "Fresh",
            "Fragrant", "Funky", "Garlicky", "Greasy", "Healthy", "Hearty", "Herby", "Hot", "Hot and sour",
            "Indulgent", "Juicy", "Light", "Mild", "Numbing", "Nutty", "Peppery", "Pickled", "Refreshing",
            "Rich", "Salty", "Savory", "Silky", "Smoky", "Sour", "Sweet", "Sweet and sour", "Tangy", "Tart",
            "Tender", "Umami", "Zesty");

    private PlaceTags() {
    }

    public static List<String> assign(String name, String primaryType, List<String> types) {
        List<String> tags = new ArrayList<>();
        add(tags, label(primaryType));
        if (types != null) {
            for (String type : types) {
                add(tags, label(type));
            }
        }
        addFromName(tags, name);
        return List.copyOf(tags);
    }

    /** Turns tags saved earlier, including a raw Google type, into the same category labels. */
    public static List<String> keep(String name, List<String> stored) {
        List<String> tags = new ArrayList<>();
        if (stored != null) {
            for (String tag : stored) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                if (raw(tag)) {
                    add(tags, label(tag));
                } else {
                    add(tags, tag.trim());
                }
            }
        }
        addFromName(tags, name);
        return List.copyOf(tags);
    }

    private static void addFromName(List<String> tags, String name) {
        for (String word : FoodWords.mentioned(name)) {
            if (category(word)) {
                add(tags, word);
            }
        }
    }

    private static boolean category(String word) {
        return !FoodWords.isRestriction(word) && !SENSORY.contains(word);
    }

    private static void add(List<String> tags, String label) {
        if (label == null || label.isBlank() || tags.size() >= MAX) {
            return;
        }
        for (String existing : tags) {
            if (existing.equalsIgnoreCase(label)) {
                return;
            }
        }
        tags.add(label);
    }

    private static boolean raw(String tag) {
        String key = tag.trim().toLowerCase(Locale.ROOT);
        return key.contains("_") || LABELS.containsKey(key) || SKIP.contains(key);
    }

    private static String label(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String key = type.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (SKIP.contains(key)) {
            return "";
        }
        String known = LABELS.get(key);
        if (known != null) {
            return known;
        }
        String phrase = key;
        for (String suffix : List.of("_restaurant", "_shop", "_store", "_house")) {
            if (phrase.endsWith(suffix) && phrase.length() > suffix.length()) {
                phrase = phrase.substring(0, phrase.length() - suffix.length());
                break;
            }
        }
        phrase = phrase.replace('_', ' ').trim();
        if (phrase.isEmpty() || SKIP.contains(phrase.replace(' ', '_'))) {
            return "";
        }
        String canonical = FoodWords.canonical(phrase);
        if (!canonical.isEmpty()) {
            return canonical;
        }
        return title(phrase);
    }

    private static String title(String words) {
        StringBuilder label = new StringBuilder();
        for (String word : words.split(" ")) {
            if (word.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return label.toString();
    }
}
