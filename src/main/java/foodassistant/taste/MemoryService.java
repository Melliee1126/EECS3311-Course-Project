package foodassistant.taste;

import foodassistant.account.SupabaseDatabase;
import foodassistant.model.Collection;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.model.UserPreferences;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads and stores one account's taste, reviews, and saved places. */
public final class MemoryService {
    private final SupabaseDatabase database;
    private final TasteFile file = new TasteFile(Path.of("data", "taste-profiles.json"));
    private final Object gate = new Object();
    private boolean remoteUnavailable;

    public MemoryService(SupabaseDatabase database) {
        this.database = database;
    }

    public UserPreferences retrieveRelevantMemory(String accountId) {
        synchronized (gate) {
            return loadUnlocked(accountId);
        }
    }

    public void saveMemory(String accountId, UserPreferences preferences) {
        synchronized (gate) {
            saveUnlocked(accountId, preferences);
        }
    }

    public void updateMemory(String accountId, UserPreferences preferences) {
        saveMemory(accountId, preferences);
    }

    public UserPreferences saveExplicit(String accountId, List<String> interests, String description) {
        synchronized (gate) {
            UserPreferences current = loadUnlocked(accountId);
            UserPreferences next = current.withExplicit(interests, description);
            saveUnlocked(accountId, next);
            return next;
        }
    }

    public Snapshot remember(String accountId) {
        synchronized (gate) {
            return new Snapshot(loadUnlocked(accountId), savedUnlocked(accountId), database.reviews(accountId));
        }
    }

    public void rebuild(String accountId) {
        synchronized (gate) {
            UserPreferences current = loadUnlocked(accountId);
            List<Restaurant> saved = savedUnlocked(accountId);
            UserPreferences learned = TasteProfileUpdater.learn(current, database.reviews(accountId), saved);
            saveUnlocked(accountId, learned);
        }
    }

    private UserPreferences loadUnlocked(String accountId) {
        if (remoteUnavailable) {
            return file.load(accountId);
        }
        try {
            UserPreferences stored = database.taste(accountId);
            return stored == null ? UserPreferences.empty() : stored;
        } catch (RuntimeException exception) {
            if (!missingTable(exception)) {
                throw exception;
            }
            remoteUnavailable = true;
            System.err.println("Taste profiles are stored in data/taste-profiles.json until supabase/schema.sql is run.");
            return file.load(accountId);
        }
    }

    private void saveUnlocked(String accountId, UserPreferences preferences) {
        if (remoteUnavailable) {
            file.save(accountId, preferences);
            return;
        }
        try {
            database.saveTaste(accountId, preferences);
        } catch (RuntimeException exception) {
            if (!missingTable(exception)) {
                throw exception;
            }
            remoteUnavailable = true;
            System.err.println("Taste profiles are stored in data/taste-profiles.json until supabase/schema.sql is run.");
            file.save(accountId, preferences);
        }
    }

    private List<Restaurant> savedUnlocked(String accountId) {
        Map<String, Restaurant> byId = new LinkedHashMap<>();
        for (Collection collection : database.collections(accountId)) {
            for (Restaurant restaurant : collection.getPlaces()) {
                byId.putIfAbsent(restaurant.getRestId(), restaurant);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static boolean missingTable(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("taste_profiles");
    }

    public record Snapshot(UserPreferences preferences, List<Restaurant> saved, List<Review> reviews) {
    }
}
