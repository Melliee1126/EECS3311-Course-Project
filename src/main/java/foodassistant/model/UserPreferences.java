package foodassistant.model;

import java.util.ArrayList;
import java.util.List;

/** What this account likes, typed by them and learned from how they use the app. */
public final class UserPreferences {
    private final List<String> interests;
    private final List<String> likes;
    private final List<String> dislikes;
    private final String priceComfort;
    private final String description;

    public UserPreferences(
            List<String> interests,
            List<String> likes,
            List<String> dislikes,
            String priceComfort,
            String description) {
        this.interests = clean(interests, 8);
        this.likes = clean(likes, 12);
        this.dislikes = clean(dislikes, 12);
        this.priceComfort = price(priceComfort);
        this.description = clip(description);
    }

    public static UserPreferences empty() {
        return new UserPreferences(List.of(), List.of(), List.of(), "", "");
    }

    public UserPreferences withExplicit(List<String> interests, String description) {
        return new UserPreferences(interests, likes, dislikes, priceComfort, description);
    }

    public UserPreferences withLearned(List<String> likes, List<String> dislikes, String priceComfort) {
        return new UserPreferences(interests, likes, dislikes, priceComfort, description);
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
}
