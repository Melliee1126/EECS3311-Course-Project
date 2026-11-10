package foodassistant.review;

import foodassistant.account.SupabaseDatabase;
import foodassistant.feedback.FeedbackPublisher;
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

    private final SupabaseDatabase database;
    private final FeedbackPublisher feedback;

    public ReviewBoard(SupabaseDatabase database, FeedbackPublisher feedback) {
        this.database = database;
        this.feedback = feedback;
    }

    public List<Review> latest(String accountId) {
        return database.reviews(accountId);
    }

    public Review publish(String accountId, Map<String, Object> body) {
        Review review = review("r:" + UUID.randomUUID(), body);
        database.insertReview(accountId, review, Instant.now().toString());
        feedback.notifyObservers(accountId, review);
        return review;
    }

    public Review update(String accountId, String id, Map<String, Object> body) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("That review was not found.");
        }
        Review review = review(id, body);
        database.updateReview(accountId, review);
        feedback.notifyObservers(accountId, review);
        return review;
    }

    public void delete(String accountId, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("That review was not found.");
        }
        database.deleteReview(accountId, id);
        feedback.notifyObservers(accountId, null);
    }

    private static Review review(String id, Map<String, Object> body) {
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
        List<String> photos = photos(body);
        if (photos.size() > ReviewPhotos.MAX_PHOTOS) {
            throw new IllegalArgumentException("Add up to 4 photos.");
        }
        for (String photo : photos) {
            if (photo.length() > ReviewPhotos.MAX_PHOTO) {
                throw new IllegalArgumentException("That photo is too large.");
            }
        }
        return new Review(
                id,
                restId,
                name.trim(),
                text(body, "address"),
                number(body.get("latitude")),
                number(body.get("longitude")),
                rating,
                written,
                tags,
                ReviewPhotos.encode(photos));
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
        List<String> photos = ReviewPhotos.decode(review.getPhoto());
        row.put("photo", photos.isEmpty() ? "" : photos.get(0));
        row.put("photos", photos);
        return row;
    }

    private static List<String> photos(Map<String, Object> body) {
        Object raw = body.get("photos");
        if (raw instanceof List<?> list) {
            List<String> photos = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String text && !text.isBlank()) {
                    photos.add(text);
                }
            }
            return photos;
        }
        return ReviewPhotos.decode(text(body, "photo"));
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
