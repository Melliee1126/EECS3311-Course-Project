package foodassistant.model;

import java.util.List;

/** A review one person wrote about a restaurant. */
public final class Review {
    private final String id;
    private final String restId;
    private final String restaurantName;
    private final String address;
    private final Double latitude;
    private final Double longitude;
    private final int rating;
    private final String body;
    private final List<String> tags;
    private final String photo;

    public Review(
            String id,
            String restId,
            String restaurantName,
            String address,
            Double latitude,
            Double longitude,
            int rating,
            String body,
            List<String> tags,
            String photo) {
        this.id = id;
        this.restId = restId;
        this.restaurantName = restaurantName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rating = rating;
        this.body = body;
        this.tags = List.copyOf(tags);
        this.photo = photo;
    }

    public String getId() {
        return id;
    }

    public String getRestId() {
        return restId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public String getAddress() {
        return address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public int getRating() {
        return rating;
    }

    public String getBody() {
        return body;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getPhoto() {
        return photo;
    }
}
