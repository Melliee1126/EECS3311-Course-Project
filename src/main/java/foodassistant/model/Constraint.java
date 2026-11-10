package foodassistant.model;

/** One limit extracted from a recommendation request. */
public final class Constraint {
    public static final String CUISINE = "cuisine";
    public static final String BUDGET = "budget";
    public static final String DIETARY = "dietary";
    public static final String AVOID = "avoid";
    public static final String DISTANCE = "distance";
    public static final String OCCASION = "occasion";

    private final String type;
    private final String value;
    private final int priority;

    public Constraint(String type, String value, int priority) {
        this.type = type;
        this.value = value == null ? "" : value.trim();
        this.priority = priority;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isSatisfiedBy(Restaurant restaurant) {
        if (value.isEmpty()) {
            return true;
        }
        String described = describe(restaurant);
        return switch (type) {
            case AVOID -> !FoodWords.containsWord(described, value);
            case BUDGET -> allowsPrice(restaurant.getPriceRange(), value);
            case CUISINE, DIETARY -> FoodWords.containsWord(described, value);
            default -> true;
        };
    }

    private static boolean allowsPrice(String priceRange, String ceiling) {
        if (priceRange == null || priceRange.isBlank()) {
            return true;
        }
        String price = UserPreferences.price(priceRange);
        String max = UserPreferences.price(ceiling);
        if (price.isEmpty() || max.isEmpty()) {
            return true;
        }
        return price.length() <= max.length();
    }

    private static String describe(Restaurant restaurant) {
        return restaurant.getName() + " " + restaurant.getCuisine() + " " + String.join(" ", restaurant.getTags());
    }
}
