package foodassistant.review;

import foodassistant.account.SupabaseDatabase;
import foodassistant.model.Review;
import foodassistant.server.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Checks a review and stores it for the signed-in person. */
public final class ReviewBoard {
    private static final int MAX_BODY = 500;
    private static final int MAX_TAGS = 8;
    private static final int MAX_TAG_LENGTH = 24;
    private static final int MAX_PHOTO = 150_000;

    private final SupabaseDatabase database;

    public ReviewBoard(SupabaseDatabase database) {
        this.database = database;
    }

    public List<Review> latest(String accountId) {
        return database.reviews(accountId);
    }

    public Review publish(String accountId, Map<String, Object> body) {
        String restId = text(body, "restId");
        String name = text(body, "name");
        if (restId.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("Add a restaurant first.");
        }
        int rating = rating(body.get("rating"));
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Choose a rating.");
        }
        String written = text(body, "body").trim();
        if (written.isEmpty()) {
            throw new IllegalArgumentException("Write something first.");
        }
        if (written.length() > MAX_BODY) {
            throw new IllegalArgumentException("Use 500 characters or fewer.");
        }
        List<String> tags = tags(body.get("tags"));
        String photo = text(body, "photo");
        if (photo.length() > MAX_PHOTO) {
            throw new IllegalArgumentException("That photo is too large.");
        }
        Review review = new Review(
                "r:" + UUID.randomUUID(),
                restId,
                name.trim(),
                text(body, "address"),
                number(body.get("latitude")),
                number(body.get("longitude")),
                rating,
                written,
                tags,
                photo);
        database.insertReview(accountId, review, Instant.now().toString());
        return review;
    }

    public static String listResponse(List<Review> reviews) {
        return Json.stringify(Map.of("reviews", encoded(reviews)));
    }

    public static String publishedResponse(Review review, List<Review> reviews) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("review", encoded(review));
        payload.put("reviews", encoded(reviews));
        return Json.stringify(payload);
    }

    private static List<Map<String, Object>> encoded(List<Review> reviews) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Review review : reviews) {
            rows.add(encoded(review));
        }
        return rows;
    }

    private static Map<String, Object> encoded(Review review) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", review.getId());
        row.put("restId", review.getRestId());
        row.put("name", review.getRestaurantName());
        row.put("address", review.getAddress());
        row.put("latitude", review.getLatitude());
        row.put("longitude", review.getLongitude());
        row.put("rating", review.getRating());
        row.put("body", review.getBody());
        row.put("tags", review.getTags());
        row.put("photo", review.getPhoto());
        return row;
    }

    private static int rating(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof String text ? text : "";
    }

    private static List<String> tags(Object value) {
        List<String> tags = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return tags;
        }
        for (Object item : list) {
            if (!(item instanceof String text)) {
                continue;
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_TAG_LENGTH || tags.contains(trimmed)) {
                continue;
            }
            tags.add(trimmed);
            if (tags.size() == MAX_TAGS) {
                break;
            }
        }
        return tags;
    }
}
