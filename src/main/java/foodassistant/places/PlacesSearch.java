package foodassistant.places;

import foodassistant.model.Location;
import foodassistant.model.Restaurant;
import foodassistant.server.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Asks Places API (New) for restaurants. The website and the command line both use this. */
public final class PlacesSearch {
    private static final URI SEARCH = URI.create("https://places.googleapis.com/v1/places:searchText");
    private static final String FIELDS = "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.priceLevel,places.primaryType,places.photos";
    private static final double BIAS_METERS = 20_000;
    private static final int MAX_PHOTO = 2_000_000;

    private final String apiKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private PlacesSearch(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public static PlacesSearch fromFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new PlacesSearch("");
        }
        String key = "";
        for (String line : Files.readString(file).split("\\R")) {
            int split = line.indexOf('=');
            if (split <= 0 || line.startsWith("#")) {
                continue;
            }
            String name = line.substring(0, split).trim();
            if ("PLACES_API_KEY".equals(name)) {
                key = line.substring(split + 1).trim();
            }
        }
        return new PlacesSearch(key);
    }

    public List<Hit> search(String query, double latitude, double longitude) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Add PLACES_API_KEY to supabase.local. Enable Places API (New) for that key.");
        }
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Type a restaurant search.");
        }
        HttpRequest request = HttpRequest.newBuilder(SEARCH)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELDS)
                .header("Referer", "http://localhost:8080/")
                .POST(HttpRequest.BodyPublishers.ofString(body(text, latitude, longitude)))
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalArgumentException(googleMessage(response.body()));
        }
        return hits(response.body());
    }

    public Photo photo(String name) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Add PLACES_API_KEY to supabase.local. Enable Places API (New) for that key.");
        }
        if (!photoName(name)) {
            throw new IllegalArgumentException("That photo was not found.");
        }
        URI uri = URI.create("https://places.googleapis.com/v1/" + name + "/media?maxHeightPx=400&maxWidthPx=400&skipHttpRedirect=true");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("X-Goog-Api-Key", apiKey)
                .header("Referer", "http://localhost:8080/")
                .GET()
                .build();
        HttpResponse<String> lookup = send(request, HttpResponse.BodyHandlers.ofString());
        String photoUri = photoUri(lookup.body());
        if (lookup.statusCode() != 200 || !googleImage(photoUri)) {
            throw new IllegalArgumentException("That photo was not found.");
        }
        HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(photoUri))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<byte[]> image = send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
        String type = image.headers().firstValue("Content-Type").orElse("image/jpeg");
        if (image.statusCode() != 200 || !type.startsWith("image/") || image.body().length > MAX_PHOTO) {
            throw new IllegalArgumentException("That photo was not found.");
        }
        return new Photo(image.body(), type);
    }

    public static String response(List<Hit> hits) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Hit hit : hits) {
            rows.add(row(hit));
        }
        return Json.stringify(Map.of("restaurants", rows));
    }

    private static Map<String, Object> row(Hit hit) {
        Restaurant restaurant = hit.restaurant();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("restId", restaurant.getRestId());
        row.put("name", restaurant.getName());
        row.put("cuisine", restaurant.getCuisine());
        row.put("address", restaurant.getAddress());
        row.put("latitude", restaurant.hasLocation() ? restaurant.getLocation().getLatitude() : null);
        row.put("longitude", restaurant.hasLocation() ? restaurant.getLocation().getLongitude() : null);
        row.put("rating", restaurant.getRating());
        row.put("priceRange", restaurant.getPriceRange());
        row.put("status", restaurant.getStatus());
        row.put("tags", restaurant.getTags());
        row.put("photo", hit.photoName().isBlank()
                ? ""
                : "/api/places/photo?name=" + URLEncoder.encode(hit.photoName(), StandardCharsets.UTF_8));
        return row;
    }

    private static String body(String query, double latitude, double longitude) {
        Map<String, Object> center = new LinkedHashMap<>();
        center.put("latitude", latitude);
        center.put("longitude", longitude);
        Map<String, Object> circle = new LinkedHashMap<>();
        circle.put("center", center);
        circle.put("radius", BIAS_METERS);
        Map<String, Object> bias = new LinkedHashMap<>();
        bias.put("circle", circle);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("textQuery", query);
        body.put("includedType", "restaurant");
        body.put("strictTypeFiltering", false);
        body.put("pageSize", 20);
        body.put("locationBias", bias);
        body.put("languageCode", "en");
        body.put("regionCode", "ca");
        return Json.stringify(body);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(request, handler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Couldn't load restaurants from Google Places.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Couldn't load restaurants from Google Places.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Hit> hits(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?> raw) || !(raw.get("places") instanceof List<?> list)) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Hit hit = hit((Map<String, Object>) map);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private static Hit hit(Map<String, Object> place) {
        String name = text(place.get("displayName"));
        if (name.isBlank()) {
            return null;
        }
        String address = text(place.get("formattedAddress"));
        String id = text(place.get("id"));
        if (id.isBlank()) {
            id = name + "-" + address;
        }
        Double latitude = coordinate(place.get("location"), "latitude");
        Double longitude = coordinate(place.get("location"), "longitude");
        Location location = latitude == null || longitude == null ? null : new Location(latitude, longitude, address);
        String primaryType = text(place.get("primaryType"));
        List<String> tags = primaryType.isBlank() ? List.of() : List.of(primaryType);
        Restaurant restaurant = new Restaurant(
                "g:" + id,
                name,
                cuisine(primaryType),
                address,
                location,
                rating(place.get("rating")),
                price(place.get("priceLevel")),
                tags,
                "discovered");
        return new Hit(restaurant, photoName(place.get("photos")));
    }

    @SuppressWarnings("unchecked")
    private static String photoName(Object photos) {
        if (!(photos instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> raw)) {
            return "";
        }
        Map<String, Object> photo = (Map<String, Object>) raw;
        String name = text(photo.get("name"));
        return photoName(name) ? name : "";
    }

    private static String photoUri(String json) {
        try {
            Object parsed = Json.parse(json);
            if (parsed instanceof Map<?, ?> map && map.get("photoUri") instanceof String uri) {
                return uri;
            }
        } catch (RuntimeException ignored) {
            return "";
        }
        return "";
    }

    private static boolean googleImage(String uri) {
        try {
            URI parsed = URI.create(uri);
            String host = parsed.getHost();
            return "https".equalsIgnoreCase(parsed.getScheme())
                    && host != null
                    && host.endsWith(".googleusercontent.com");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean photoName(String name) {
        return name.startsWith("places/") && name.contains("/photos/") && !name.contains("..") && !name.contains("?");
    }

    private static String cuisine(String primaryType) {
        if (primaryType.isBlank()) {
            return "Restaurant";
        }
        StringBuilder label = new StringBuilder();
        for (String word : primaryType.split("_")) {
            if (word.isBlank() || word.equals("restaurant")) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return label.isEmpty() ? "Restaurant" : label.toString();
    }

    private static double rating(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static String price(Object value) {
        if (value == null) {
            return "";
        }
        return switch (String.valueOf(value).toUpperCase(Locale.ROOT)) {
            case "0", "1", "FREE", "INEXPENSIVE", "PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE" -> "$";
            case "2", "MODERATE", "PRICE_LEVEL_MODERATE" -> "$$";
            case "3", "EXPENSIVE", "PRICE_LEVEL_EXPENSIVE" -> "$$$";
            case "4", "VERY_EXPENSIVE", "PRICE_LEVEL_VERY_EXPENSIVE" -> "$$$$";
            default -> "";
        };
    }

    @SuppressWarnings("unchecked")
    private static Double coordinate(Object location, String axis) {
        if (!(location instanceof Map<?, ?> raw)) {
            return null;
        }
        Object value = ((Map<String, Object>) raw).get(axis);
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        return null;
    }

    private static String text(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> map && map.get("text") instanceof String text) {
            return text;
        }
        return "";
    }

    private static String googleMessage(String body) {
        try {
            Object parsed = Json.parse(body);
            if (parsed instanceof Map<?, ?> raw && raw.get("error") instanceof Map<?, ?> error && error.get("message") instanceof String message && !message.isBlank()) {
                if (message.toLowerCase(Locale.ROOT).contains("api key") || message.toLowerCase(Locale.ROOT).contains("permission")) {
                    return "Google Places rejected the server key. Enable Places API (New), and do not restrict that key to a website address.";
                }
                return "Couldn't load restaurants from Google Places.";
            }
        } catch (RuntimeException ignored) {
            return "Couldn't load restaurants from Google Places.";
        }
        return "Couldn't load restaurants from Google Places.";
    }

    public record Hit(Restaurant restaurant, String photoName) {
    }

    public record Photo(byte[] bytes, String contentType) {
    }
}
