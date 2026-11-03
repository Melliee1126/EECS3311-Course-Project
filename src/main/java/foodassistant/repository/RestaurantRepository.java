package foodassistant.repository;

import foodassistant.model.Location;
import foodassistant.model.Restaurant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RestaurantRepository {
    private static final Pattern OBJECT = Pattern.compile("\\{(.*?)}", Pattern.DOTALL);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final List<Restaurant> restaurants;

    public RestaurantRepository(List<Restaurant> restaurants) {
        this.restaurants = List.copyOf(restaurants);
    }

    public static RestaurantRepository load(Path path) throws IOException {
        return new RestaurantRepository(parse(Files.readString(path)));
    }

    public List<Restaurant> findByFilters(String query, String cuisine) {
        String needle = normalize(query);
        String cuisineNeedle = normalize(cuisine);
        List<Restaurant> matches = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            if (!cuisineNeedle.isEmpty() && !normalize(restaurant.getCuisine()).contains(cuisineNeedle)) {
                continue;
            }
            if (!needle.isEmpty() && !haystack(restaurant).contains(needle)) {
                continue;
            }
            matches.add(restaurant);
        }
        return matches;
    }

    public List<Restaurant> findByLocation(double latitude, double longitude, double radiusKm) {
        List<Restaurant> nearby = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            if (!restaurant.hasLocation()) {
                continue;
            }
            double distance = distanceKm(
                    latitude,
                    longitude,
                    restaurant.getLocation().getLatitude(),
                    restaurant.getLocation().getLongitude());
            if (distance <= radiusKm) {
                nearby.add(restaurant);
            }
        }
        return nearby;
    }

    public List<Restaurant> withLocation(List<Restaurant> source) {
        List<Restaurant> pinned = new ArrayList<>();
        for (Restaurant restaurant : source) {
            if (restaurant.hasLocation()) {
                pinned.add(restaurant);
            }
        }
        return pinned;
    }

    static List<Restaurant> parse(String json) {
        List<Restaurant> parsed = new ArrayList<>();
        Matcher objects = OBJECT.matcher(json);
        while (objects.find()) {
            String body = objects.group(1);
            Double latitude = numberField(body, "latitude");
            Double longitude = numberField(body, "longitude");
            Location location = latitude == null || longitude == null
                    ? null
                    : new Location(latitude, longitude, textField(body, "address"));
            parsed.add(new Restaurant(
                    textField(body, "restId"),
                    textField(body, "name"),
                    textField(body, "cuisine"),
                    textField(body, "address"),
                    location,
                    numberField(body, "rating") == null ? 0 : numberField(body, "rating"),
                    textField(body, "priceRange"),
                    textListField(body, "tags"),
                    textField(body, "status")));
        }
        return parsed;
    }

    private static String haystack(Restaurant restaurant) {
        return normalize(String.join(
                " ",
                restaurant.getName(),
                restaurant.getCuisine(),
                restaurant.getAddress(),
                restaurant.getStatus(),
                String.join(" ", restaurant.getTags())));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String textField(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private static Double numberField(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(null|-?\\d+(?:\\.\\d+)?)").matcher(body);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return null;
        }
        return Double.valueOf(matcher.group(1));
    }

    private static List<String> textListField(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(body);
        if (!matcher.find()) {
            return List.of();
        }
        Matcher items = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(matcher.group(1));
        List<String> values = new ArrayList<>();
        while (items.find()) {
            values.add(items.group(1));
        }
        return values;
    }

    private static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
