package foodassistant.account;

import foodassistant.model.Collection;
import foodassistant.model.Location;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
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
import java.util.Map;

/** Reads and writes accounts and collections in the Supabase project. */
public final class SupabaseDatabase {
    private final String origin;
    private final String secret;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private SupabaseDatabase(String origin, String secret) {
        this.origin = origin;
        this.secret = secret;
    }

    public static SupabaseDatabase open(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("Missing " + file + ". Add the Supabase URL and secret key there.");
        }
        String url = "";
        String secret = "";
        for (String line : Files.readString(file).split("\\R")) {
            int split = line.indexOf('=');
            if (split <= 0 || line.startsWith("#")) {
                continue;
            }
            String key = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();
            if ("SUPABASE_URL".equals(key)) {
                url = origin(value);
            } else if ("SUPABASE_SECRET_KEY".equals(key)) {
                secret = value;
            }
        }
        if (url.isBlank() || secret.isBlank()) {
            throw new IOException("supabase.local needs SUPABASE_URL and SUPABASE_SECRET_KEY.");
        }
        SupabaseDatabase database = new SupabaseDatabase(url, secret);
        database.checkReady();
        return database;
    }

    public List<AccountRow> accounts() {
        return accountRows(request("GET", "accounts?select=id,name,email,salt,password_hash,birthday", null));
    }

    public List<SessionRow> sessions() {
        return sessionRows(request("GET", "sessions?select=token,account_id", null));
    }

    public void insertAccount(AccountRow account) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", account.id());
        row.put("name", account.name());
        row.put("email", account.email());
        row.put("salt", account.salt());
        row.put("password_hash", account.passwordHash());
        row.put("birthday", account.birthday());
        request("POST", "accounts", Json.stringify(row));
    }

    public void updateProfile(String id, String name, String email, String birthday) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("email", email);
        row.put("birthday", birthday);
        request("PATCH", "accounts?" + eq("id", id), Json.stringify(row));
    }

    public void updatePassword(String id, String salt, String passwordHash) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("salt", salt);
        row.put("password_hash", passwordHash);
        request("PATCH", "accounts?" + eq("id", id), Json.stringify(row));
    }

    public void insertSession(String token, String accountId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("token", token);
        row.put("account_id", accountId);
        request("POST", "sessions", Json.stringify(row));
    }

    public void deleteSession(String token) {
        request("DELETE", "sessions?" + eq("token", token), null);
    }

    public List<Review> reviews(String accountId) {
        String json = request(
                "GET",
                "reviews?select=id,rest_id,restaurant_name,address,latitude,longitude,rating,body,tags,photo&"
                        + eq("account_id", accountId) + "&order=created_at.desc",
                null);
        return reviewRows(json);
    }

    public void insertReview(String accountId, Review review, String createdAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", review.getId());
        row.put("account_id", accountId);
        row.put("rest_id", review.getRestId());
        row.put("restaurant_name", review.getRestaurantName());
        row.put("address", review.getAddress());
        row.put("latitude", review.getLatitude());
        row.put("longitude", review.getLongitude());
        row.put("rating", review.getRating());
        row.put("body", review.getBody());
        row.put("tags", review.getTags());
        row.put("photo", review.getPhoto());
        row.put("created_at", createdAt);
        request("POST", "reviews", Json.stringify(row));
    }

    public List<Collection> collections(String accountId) {
        String filter = eq("account_id", accountId);
        List<Collection> collections = new ArrayList<>();
        Map<String, List<Restaurant>> places = placesByCollection(request(
                "GET",
                "places?select=collection_id,rest_id,name,cuisine,address,latitude,longitude,rating,price_range,status,tags,position&"
                        + filter + "&order=position.asc",
                null));
        Object parsed = Json.parse(request(
                "GET",
                "collections?select=id,name,position&" + filter + "&order=position.asc",
                null));
        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                String id = text(map, "id");
                collections.add(new Collection(id, text(map, "name"), places.getOrDefault(id, List.of())));
            }
        }
        return collections;
    }

    public void replaceCollections(String accountId, List<Collection> collections) {
        String filter = eq("account_id", accountId);
        request("DELETE", "places?" + filter, null);
        request("DELETE", "collections?" + filter, null);
        List<Map<String, Object>> collectionRows = new ArrayList<>();
        List<Map<String, Object>> placeRows = new ArrayList<>();
        for (int i = 0; i < collections.size(); i++) {
            Collection collection = collections.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("account_id", accountId);
            row.put("id", collection.getId());
            row.put("name", collection.getName());
            row.put("position", i);
            collectionRows.add(row);
            List<Restaurant> places = collection.getPlaces();
            for (int placeIndex = 0; placeIndex < places.size(); placeIndex++) {
                placeRows.add(placeRow(accountId, collection.getId(), places.get(placeIndex), placeIndex));
            }
        }
        if (!collectionRows.isEmpty()) {
            request("POST", "collections", Json.stringify(collectionRows));
        }
        if (!placeRows.isEmpty()) {
            request("POST", "places", Json.stringify(placeRows));
        }
    }

    private void checkReady() {
        try {
            request("GET", "accounts?select=id&limit=1", null);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("accounts")) {
                throw new IllegalStateException(
                        "Supabase does not have the accounts table yet. In the Supabase SQL editor, run supabase/schema.sql.",
                        exception);
            }
            throw exception;
        }
    }

    private Map<String, List<Restaurant>> placesByCollection(String json) {
        Map<String, List<Restaurant>> grouped = new LinkedHashMap<>();
        Object parsed = Json.parse(json);
        if (!(parsed instanceof List<?> list)) {
            return grouped;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            String collectionId = text(map, "collection_id");
            grouped.computeIfAbsent(collectionId, key -> new ArrayList<>()).add(restaurant(map));
        }
        return grouped;
    }

    private static Map<String, Object> placeRow(String accountId, String collectionId, Restaurant restaurant, int position) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account_id", accountId);
        row.put("collection_id", collectionId);
        row.put("rest_id", restaurant.getRestId());
        row.put("name", restaurant.getName());
        row.put("cuisine", restaurant.getCuisine());
        row.put("address", restaurant.getAddress());
        row.put("latitude", restaurant.hasLocation() ? restaurant.getLocation().getLatitude() : null);
        row.put("longitude", restaurant.hasLocation() ? restaurant.getLocation().getLongitude() : null);
        row.put("rating", restaurant.getRating());
        row.put("price_range", restaurant.getPriceRange());
        row.put("status", restaurant.getStatus());
        row.put("tags", restaurant.getTags());
        row.put("position", position);
        return row;
    }

    private static Restaurant restaurant(Map<String, Object> map) {
        Double latitude = number(map, "latitude");
        Double longitude = number(map, "longitude");
        String address = text(map, "address");
        Location location = latitude == null || longitude == null ? null : new Location(latitude, longitude, address);
        Double rating = number(map, "rating");
        return new Restaurant(
                text(map, "rest_id"),
                text(map, "name"),
                text(map, "cuisine"),
                address,
                location,
                rating == null ? 0 : rating,
                text(map, "price_range"),
                tags(map.get("tags")),
                text(map, "status").isBlank() ? "discovered" : text(map, "status"));
    }

    private String request(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + "/rest/v1/" + path))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", secret)
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.header("Prefer", "return=minimal");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(failure(response.statusCode(), response.body()));
            }
            return response.body() == null || response.body().isBlank() ? "[]" : response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Supabase did not answer.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not reach Supabase.", exception);
        }
    }

    private static String failure(int status, String body) {
        Object parsed = null;
        try {
            parsed = Json.parse(body);
        } catch (RuntimeException ignored) {
            parsed = null;
        }
        if (parsed instanceof Map<?, ?> map && map.get("message") instanceof String message && !message.isBlank()) {
            return "Supabase returned " + status + ": " + message;
        }
        return "Supabase returned " + status + ".";
    }

    @SuppressWarnings("unchecked")
    private static List<Review> reviewRows(String json) {
        Object parsed = Json.parse(json);
        List<Review> rows = new ArrayList<>();
        if (!(parsed instanceof List<?> list)) {
            return rows;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) raw;
            Double rating = number(map, "rating");
            rows.add(new Review(
                    text(map, "id"),
                    text(map, "rest_id"),
                    text(map, "restaurant_name"),
                    text(map, "address"),
                    number(map, "latitude"),
                    number(map, "longitude"),
                    rating == null ? 0 : rating.intValue(),
                    text(map, "body"),
                    tags(map.get("tags")),
                    text(map, "photo")));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<AccountRow> accountRows(String json) {
        Object parsed = Json.parse(json);
        List<AccountRow> rows = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> map = (Map<String, Object>) raw;
                    rows.add(new AccountRow(
                            text(map, "id"),
                            text(map, "name"),
                            text(map, "email"),
                            text(map, "salt"),
                            text(map, "password_hash"),
                            text(map, "birthday")));
                }
            }
        }
        return List.copyOf(rows);
    }

    @SuppressWarnings("unchecked")
    private static List<SessionRow> sessionRows(String json) {
        Object parsed = Json.parse(json);
        List<SessionRow> rows = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> map = (Map<String, Object>) raw;
                    String token = text(map, "token");
                    String accountId = text(map, "account_id");
                    if (!token.isBlank() && !accountId.isBlank()) {
                        rows.add(new SessionRow(token, accountId));
                    }
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<String> tags(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            value = Json.parse(text);
        }
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

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String text ? text : "";
    }

    private static Double number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String eq(String column, String value) {
        return column + "=eq." + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String origin(String url) {
        String trimmed = url == null ? "" : url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String rest = "/rest/v1";
        if (trimmed.endsWith(rest)) {
            trimmed = trimmed.substring(0, trimmed.length() - rest.length());
        }
        return trimmed;
    }

    public record AccountRow(String id, String name, String email, String salt, String passwordHash, String birthday) {
    }

    public record SessionRow(String token, String accountId) {
    }
}
