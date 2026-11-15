package foodassistant.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What this account likes, typed by them and learned from how they use the app. */
public final class UserPreferences {
    private final List<String> interests;
    private final List<String> likes;
    private final List<String> dislikes;
    private final String priceComfort;
    private final String description;
    private final Map<String, Integer> stated;
    private final Map<String, Integer> affinity;

    public UserPreferences(
            List<String> interests,
            List<String> likes,
            List<String> dislikes,
            String priceComfort,
            String description) {
        this(interests, likes, dislikes, priceComfort, description, Map.of(), Map.of());
    }

    public UserPreferences(
            List<String> interests,
            List<String> likes,
            List<String> dislikes,
            String priceComfort,
            String description,
            Map<String, Integer> stated,
            Map<String, Integer> affinity) {
        this.interests = clean(interests, 8);
        this.likes = clean(likes, 12);
        this.dislikes = clean(dislikes, 12);
        this.priceComfort = price(priceComfort);
        this.description = clip(description);
        this.stated = scores(stated);
        this.affinity = scores(affinity);
    }

    public static UserPreferences empty() {
        return new UserPreferences(List.of(), List.of(), List.of(), "", "");
    }

    public UserPreferences withExplicit(List<String> interests, String description) {
        return new UserPreferences(interests, likes, dislikes, priceComfort, description, stated, affinity);
    }

    public UserPreferences withLearned(List<String> likes, List<String> dislikes, String priceComfort) {
        return withLearned(likes, dislikes, priceComfort, affinity);
    }

    public UserPreferences withLearned(
            List<String> likes,
            List<String> dislikes,
            String priceComfort,
            Map<String, Integer> affinity) {
        return new UserPreferences(interests, likes, dislikes, priceComfort, description, stated, affinity);
    }

    public UserPreferences withSignals(Map<String, Integer> stated, Map<String, Integer> affinity) {
        return new UserPreferences(interests, likes, dislikes, priceComfort, description, stated, affinity);
    }

    public UserPreferences plusStated(Map<String, Integer> delta) {
        Map<String, Integer> merged = new LinkedHashMap<>(stated);
        if (delta != null) {
            for (Map.Entry<String, Integer> entry : delta.entrySet()) {
                if (entry.getValue() == null || entry.getValue() == 0 || entry.getKey() == null) {
                    continue;
                }
                String canonical = FoodWords.canonical(entry.getKey());
                String kept = canonical.isEmpty() ? entry.getKey().trim() : canonical;
                if (kept.isEmpty() || kept.length() > 24) {
                    continue;
                }
                int current = merged.getOrDefault(kept, 0);
                if (FoodWords.isDiet(kept) && current >= 8 && entry.getValue() < 0) {
                    continue;
                }
                if (FoodWords.isRestriction(kept) && entry.getValue() <= -4) {
                    merged.put(kept, Math.max(-12, entry.getValue()));
                    continue;
                }
                if (FoodWords.isRestriction(kept) && current <= -4 && entry.getValue() > 0) {
                    if (entry.getValue() >= 2) {
                        merged.put(kept, Math.min(12, entry.getValue()));
                    }
                    continue;
                }
                int next = Math.max(-12, Math.min(12, current + entry.getValue()));
                if (next == 0) {
                    merged.remove(kept);
                } else {
                    merged.put(kept, next);
                }
            }
        }
        return new UserPreferences(interests, likes, dislikes, priceComfort, description, merged, affinity);
    }

    public UserPreferences withoutStated(List<String> foods) {
        if (foods == null || foods.isEmpty() || stated.isEmpty()) {
            return this;
        }
        Map<String, Integer> merged = new LinkedHashMap<>(stated);
        for (String food : foods) {
            String canonical = FoodWords.canonical(food);
            String kept = canonical.isEmpty() ? food == null ? "" : food.trim() : canonical;
            if (kept.isEmpty()) {
                continue;
            }
            merged.keySet().removeIf(key -> key.equalsIgnoreCase(kept));
        }
        return new UserPreferences(interests, likes, dislikes, priceComfort, description, merged, affinity);
    }

    public List<String> diets() {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stated.entrySet()) {
            if (FoodWords.isDiet(entry.getKey()) && entry.getValue() >= 8) {
                found.add(entry.getKey());
            }
        }
        found.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(found);
    }

    public List<String> avoids() {
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : stated.entrySet()) {
            if (FoodWords.isRestriction(entry.getKey()) && entry.getValue() <= -4) {
                found.add(entry.getKey());
            }
        }
        List<String> diets = diets();
        if (contains(diets, "Halal") || contains(diets, "Kosher")) {
            found.add("Pork");
        }
        if (contains(diets, "Kosher")) {
            found.add("Shellfish");
        }
        if (contains(diets, "Nut-free")) {
            found.add("Peanut");
            found.add("Tree nut");
        }
        List<String> ordered = new ArrayList<>(found);
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(ordered);
    }

    public List<String> getInterests() {
        return interests;
    }

    public List<String> getLikes() {
        return likes;
    }

    public List<String> getDislikes() {
        return dislikes;
    }

    public String getPriceComfort() {
        return priceComfort;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Integer> getStated() {
        return stated;
    }

    public Map<String, Integer> getAffinity() {
        return affinity;
    }

    public int statedScore(String word) {
        return lookup(stated, word);
    }

    public int strength(String word) {
        return lookup(affinity, word);
    }

    public String labeled(List<String> words) {
        if (words == null || words.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        for (String word : words) {
            parts.add(word + mark(strength(word)));
        }
        return String.join(", ", parts);
    }

    public static String strengthWord(int weight) {
        int magnitude = Math.abs(weight);
        String force = magnitude >= 3 ? "strong " : magnitude <= 1 ? "mild " : "";
        return force + (weight > 0 ? "like" : "dislike");
    }

    public static List<String> clean(List<String> values, int max) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || trimmed.length() > 24) {
                continue;
            }
            boolean seen = false;
            for (String existing : cleaned) {
                if (existing.equalsIgnoreCase(trimmed)) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            cleaned.add(trimmed);
            if (cleaned.size() == max) {
                break;
            }
        }
        return List.copyOf(cleaned);
    }

    public static String clip(String description) {
        if (description == null) {
            return "";
        }
        String trimmed = description.trim();
        return trimmed.length() <= 140 ? trimmed : trimmed.substring(0, 140);
    }

    public static String price(String value) {
        if (value == null) {
            return "";
        }
        return switch (value.trim()) {
            case "$", "$$", "$$$", "$$$$" -> value.trim();
            default -> "";
        };
    }

    private static String mark(int score) {
        int magnitude = Math.abs(score);
        if (magnitude >= 3) {
            return " (strong)";
        }
        if (magnitude == 1) {
            return " (mild)";
        }
        return "";
    }

    private static boolean contains(List<String> values, String word) {
        for (String value : values) {
            if (value.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    private static int lookup(Map<String, Integer> scores, String word) {
        if (word == null || word.isBlank() || scores.isEmpty()) {
            return 0;
        }
        String canonical = FoodWords.canonical(word);
        String key = canonical.isEmpty() ? word.trim() : canonical;
        Integer score = scores.get(key);
        if (score != null) {
            return score;
        }
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private static Map<String, Integer> scores(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() == 0) {
                continue;
            }
            String canonical = FoodWords.canonical(entry.getKey());
            String kept = canonical.isEmpty() ? entry.getKey().trim() : canonical;
            if (kept.isEmpty() || kept.length() > 24) {
                continue;
            }
            int score = Math.max(-12, Math.min(12, entry.getValue()));
            cleaned.merge(kept, score, Integer::sum);
        }
        cleaned.entrySet().removeIf(entry -> entry.getValue() == 0);
        return cleaned.isEmpty() ? Map.of() : Map.copyOf(cleaned);
    }
}
