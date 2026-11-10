package foodassistant.taste;

import foodassistant.model.UserPreferences;
import foodassistant.server.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Taste profiles kept on this machine when the Supabase table is not there yet. */
final class TasteFile {
    private final Path path;

    TasteFile(Path path) {
        this.path = path;
    }

    UserPreferences load(String accountId) {
        Object row = read().get(accountId);
        if (!(row instanceof Map<?, ?> map)) {
            return UserPreferences.empty();
        }
        return preferences(map);
    }

    void save(String accountId, UserPreferences preferences) {
        Map<String, Object> all = read();
        all.put(accountId, row(preferences));
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, Json.stringify(all));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store the taste profile.", exception);
        }
    }

    private Map<String, Object> read() {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = Json.parse(Files.readString(path));
            if (parsed instanceof Map<?, ?> raw) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return copy;
            }
        } catch (IOException | RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    private static UserPreferences preferences(Map<?, ?> map) {
        return new UserPreferences(
                strings(map.get("interests")),
                strings(map.get("likes")),
                strings(map.get("dislikes")),
                text(map.get("priceComfort")),
                text(map.get("description")));
    }

    private static Map<String, Object> row(UserPreferences preferences) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("interests", preferences.getInterests());
        row.put("likes", preferences.getLikes());
        row.put("dislikes", preferences.getDislikes());
        row.put("priceComfort", preferences.getPriceComfort());
        row.put("description", preferences.getDescription());
        return row;
    }

    private static List<String> strings(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String text) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    private static String text(Object value) {
        return value instanceof String text ? text : "";
    }
}
