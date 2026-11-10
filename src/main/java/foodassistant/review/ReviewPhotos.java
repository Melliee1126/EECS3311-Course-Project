package foodassistant.review;

import foodassistant.server.Json;

import java.util.ArrayList;
import java.util.List;

/** One review can keep several photos in the existing photo column. */
public final class ReviewPhotos {
    public static final int MAX_PHOTOS = 4;
    public static final int MAX_PHOTO = 180_000;

    private ReviewPhotos() {
    }

    public static String encode(List<String> photos) {
        if (photos.isEmpty()) {
            return "";
        }
        if (photos.size() == 1) {
            return photos.get(0);
        }
        return Json.stringify(photos);
    }

    public static List<String> decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        if (!stored.startsWith("[")) {
            return List.of(stored);
        }
        Object parsed;
        try {
            parsed = Json.parse(stored);
        } catch (RuntimeException exception) {
            return List.of(stored);
        }
        if (!(parsed instanceof List<?> list)) {
            return List.of(stored);
        }
        List<String> photos = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) {
                photos.add(text);
            }
        }
        return List.copyOf(photos);
    }
}
