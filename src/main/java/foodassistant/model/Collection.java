package foodassistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A named set of restaurants the user keeps. Saved restaurants is the
 * default collection and cannot be removed.
 */
public final class Collection {
    public static final String SAVED_ID = "saved";
    public static final String SAVED_NAME = "Saved restaurants";
    public static final int MAX_NAME_LENGTH = 80;

    private final String id;
    private final String name;
    private final List<Restaurant> places;

    public Collection(String id, String name, List<Restaurant> places) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Collection id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name the collection first.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Use 80 characters or fewer.");
        }
        this.id = id;
        this.name = trimmed;
        this.places = List.copyOf(places == null ? List.of() : places);
    }

    public static Collection saved() {
        return new Collection(SAVED_ID, SAVED_NAME, List.of());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Restaurant> getPlaces() {
        return places;
    }

    public boolean isSaved() {
        return SAVED_ID.equals(id);
    }

    public boolean canDelete() {
        return !isSaved();
    }

    public boolean contains(String restId) {
        return indexOf(restId) >= 0;
    }

    public Collection add(Restaurant restaurant) {
        if (restaurant == null) {
            throw new IllegalArgumentException("Restaurant is required");
        }
        List<Restaurant> next = new ArrayList<>();
        boolean replaced = false;
        for (Restaurant place : places) {
            if (place.getRestId().equals(restaurant.getRestId())) {
                next.add(restaurant);
                replaced = true;
            } else {
                next.add(place);
            }
        }
        if (!replaced) {
            next.add(restaurant);
        }
        return new Collection(id, name, next);
    }

    public Collection remove(String restId) {
        if (!contains(restId)) {
            return this;
        }
        List<Restaurant> next = new ArrayList<>();
        for (Restaurant place : places) {
            if (!place.getRestId().equals(restId)) {
                next.add(place);
            }
        }
        return new Collection(id, name, next);
    }

    private int indexOf(String restId) {
        if (restId == null) {
            return -1;
        }
        for (int i = 0; i < places.size(); i++) {
            if (restId.equals(places.get(i).getRestId())) {
                return i;
            }
        }
        return -1;
    }
}
