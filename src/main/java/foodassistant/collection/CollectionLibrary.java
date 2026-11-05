package foodassistant.collection;

import foodassistant.account.SupabaseDatabase;
import foodassistant.model.Collection;
import foodassistant.model.Location;
import foodassistant.model.Restaurant;
import foodassistant.server.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The collections the user keeps. The default collection is Saved restaurants.
 */
public final class CollectionLibrary {
    private final SupabaseDatabase database;
    private final String accountId;
    private List<Collection> collections;

    private CollectionLibrary(SupabaseDatabase database, String accountId, List<Collection> collections) {
        this.database = database;
        this.accountId = accountId;
        this.collections = collections;
    }

    public static CollectionLibrary load(SupabaseDatabase database, String accountId) {
        List<Collection> loaded = database.collections(accountId);
        if (loaded.isEmpty()) {
            loaded = List.of(Collection.saved());
        }
        CollectionLibrary library = new CollectionLibrary(database, accountId, ensureSaved(loaded));
        library.save();
        return library;
    }

    public synchronized List<Collection> all() {
        return List.copyOf(collections);
    }

    public synchronized Collection create(String name, Restaurant place) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Name the collection first.");
        }
        for (Collection collection : collections) {
            if (collection.getName().equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException("A collection already has that name.");
            }
        }
        Collection created = new Collection("c:" + UUID.randomUUID(), trimmed, List.of());
        if (place != null) {
            created = created.add(place);
        }
        List<Collection> next = new ArrayList<>(collections);
        next.add(created);
        replace(next);
        return created;
    }

    public synchronized void addPlace(String collectionId, Restaurant place) {
        replace(update(collectionId, collection -> collection.add(place)));
    }

    public synchronized void removePlace(String collectionId, String restId) {
        replace(update(collectionId, collection -> collection.remove(restId)));
    }

    public synchronized void delete(String collectionId) {
        Collection collection = find(collectionId);
        if (!collection.canDelete()) {
            throw new IllegalArgumentException("Saved restaurants cannot be deleted.");
        }
        List<Collection> next = new ArrayList<>();
        for (Collection item : collections) {
            if (!item.getId().equals(collectionId)) {
                next.add(item);
            }
        }
        replace(next);
    }

    private List<Collection> update(String collectionId, java.util.function.UnaryOperator<Collection> change) {
        Collection current = find(collectionId);
        Collection changed = change.apply(current);
        List<Collection> next = new ArrayList<>();
        for (Collection item : collections) {
            next.add(item.getId().equals(collectionId) ? changed : item);
        }
        return next;
    }

    private Collection find(String collectionId) {
        for (Collection collection : collections) {
            if (collection.getId().equals(collectionId)) {
                return collection;
            }
        }
        throw new IllegalArgumentException("Collection was not found.");
    }

    private void replace(List<Collection> next) {
        collections = ensureSaved(next);
        save();
    }

    private void save() {
        database.replaceCollections(accountId, collections);
    }

    private static List<Collection> ensureSaved(List<Collection> source) {
        Collection saved = null;
        List<Collection> custom = new ArrayList<>();
        for (Collection collection : source) {
            if (collection.isSaved()) {
                saved = collection;
            } else {
                custom.add(collection);
            }
        }
        List<Collection> next = new ArrayList<>();
        next.add(saved == null ? Collection.saved() : new Collection(Collection.SAVED_ID, Collection.SAVED_NAME, saved.getPlaces()));
        next.addAll(custom);
        return List.copyOf(next);
    }

    static List<Collection> read(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof List<?> list)) {
            throw new IllegalArgumentException("Collections file must be a JSON array");
        }
        List<Collection> collections = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                collections.add(collection(map));
            }
        }
        return collections;
    }

    public static String toJson(List<Collection> collections) {
        List<Map<String, Object>> encoded = new ArrayList<>();
        for (Collection collection : collections) {
            encoded.add(collectionMap(collection));
        }
        return Json.stringify(encoded);
    }

    public static String collectionsResponse(List<Collection> collections, String id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<Map<String, Object>> encoded = new ArrayList<>();
        for (Collection collection : collections) {
            encoded.add(collectionMap(collection));
        }
        payload.put("collections", encoded);
        if (id != null) {
            payload.put("id", id);
        }
        return Json.stringify(payload);
    }

    @SuppressWarnings("unchecked")
    public static Restaurant restaurant(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Restaurant is required");
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        String restId = text(map, "restId");
        String name = text(map, "name");
        if (restId.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("Restaurant is required");
        }
        Double latitude = number(map, "latitude");
        Double longitude = number(map, "longitude");
        String address = text(map, "address");
        Location location = latitude == null || longitude == null ? null : new Location(latitude, longitude, address);
        return new Restaurant(
                restId,
                name,
                text(map, "cuisine"),
                address,
                location,
                number(map, "rating") == null ? 0 : number(map, "rating"),
                text(map, "priceRange"),
                strings(map.get("tags")),
                text(map, "status").isBlank() ? "discovered" : text(map, "status"));
    }

    @SuppressWarnings("unchecked")
    private static Collection collection(Map<?, ?> raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        List<Restaurant> places = new ArrayList<>();
        Object rawPlaces = map.get("places");
        if (rawPlaces instanceof List<?> list) {
            for (Object item : list) {
                places.add(restaurant(item));
            }
        }
        return new Collection(text(map, "id"), text(map, "name"), places);
    }

    private static Map<String, Object> collectionMap(Collection collection) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", collection.getId());
        map.put("name", collection.getName());
        List<Map<String, Object>> places = new ArrayList<>();
        for (Restaurant place : collection.getPlaces()) {
            places.add(restaurantMap(place));
        }
        map.put("places", places);
        return map;
    }

    private static Map<String, Object> restaurantMap(Restaurant restaurant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("restId", restaurant.getRestId());
        map.put("name", restaurant.getName());
        map.put("cuisine", restaurant.getCuisine());
        map.put("address", restaurant.getAddress());
        map.put("latitude", restaurant.hasLocation() ? restaurant.getLocation().getLatitude() : null);
        map.put("longitude", restaurant.hasLocation() ? restaurant.getLocation().getLongitude() : null);
        map.put("rating", restaurant.getRating());
        map.put("priceRange", restaurant.getPriceRange());
        map.put("status", restaurant.getStatus());
        map.put("tags", restaurant.getTags());
        return map;
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String text ? text : "";
    }

    private static Double number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static List<String> strings(Object value) {
        List<String> tags = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String text) {
                    tags.add(text);
                }
            }
        }
        return tags;
    }
}
