package foodassistant.model;

import java.util.List;

public final class Restaurant {
    private final String restId;
    private final String name;
    private final String cuisine;
    private final String address;
    private final Location location;
    private final double rating;
    private final String priceRange;
    private final List<String> tags;
    private final String status;

    public Restaurant(
            String restId,
            String name,
            String cuisine,
            String address,
            Location location,
            double rating,
            String priceRange,
            List<String> tags,
            String status) {
        this.restId = restId;
        this.name = name;
        this.cuisine = cuisine;
        this.address = address;
        this.location = location;
        this.rating = rating;
        this.priceRange = priceRange;
        this.tags = List.copyOf(tags);
        this.status = status;
    }

    public String getRestId() {
        return restId;
    }

    public String getName() {
        return name;
    }

    public String getCuisine() {
        return cuisine;
    }

    public String getAddress() {
        return address;
    }

    public Location getLocation() {
        return location;
    }

    public boolean hasLocation() {
        return location != null;
    }

    public double getRating() {
        return rating;
    }

    public String getPriceRange() {
        return priceRange;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getStatus() {
        return status;
    }

    public String getDetail() {
        String where = address == null || address.isBlank() ? "no address" : address;
        String score = rating > 0 ? String.valueOf(rating) : "unrated";
        return name + " [" + status + "] " + cuisine + " " + priceRange
                + " rating " + score + " — " + where;
    }

    public String pinLabel() {
        return name + " (" + status + ")";
    }
}
