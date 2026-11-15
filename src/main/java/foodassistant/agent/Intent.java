package foodassistant.agent;

/** Dish, cuisine, budget, location, mood, and whether to search or ask. */
public record Intent(String dish, String cuisine, String budget, String location, String intent, String style) {
    public Intent(String dish, String cuisine, String budget, String location, String intent) {
        this(dish, cuisine, budget, location, intent, "");
    }

    public Intent {
        dish = clean(dish);
        cuisine = clean(cuisine);
        budget = clean(budget);
        location = clean(location);
        style = clean(style);
        intent = "search".equals(intent) ? "search" : "ask";
    }

    public boolean search() {
        return "search".equals(intent);
    }

    public static Intent ask() {
        return new Intent("", "", "", "", "ask", "");
    }

    public Intent asSearch() {
        return new Intent(dish, cuisine, budget, location, "search", style);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80).trim();
    }
}
