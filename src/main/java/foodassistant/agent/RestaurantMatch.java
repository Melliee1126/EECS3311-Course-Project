package foodassistant.agent;

import foodassistant.model.FoodWords;
import foodassistant.model.Location;
import foodassistant.model.Restaurant;

final class RestaurantMatch {
    private static final double EARTH_RADIUS_KM = 6371;

    private RestaurantMatch() {
    }

    static boolean matches(Restaurant restaurant, String word) {
        return FoodWords.containsWord(describe(restaurant), word);
    }

    static String describe(Restaurant restaurant) {
        return restaurant.getName() + " " + restaurant.getCuisine() + " " + String.join(" ", restaurant.getTags());
    }

    static double kilometers(Restaurant restaurant, double latitude, double longitude) {
        if (!restaurant.hasLocation()) {
            return -1;
        }
        Location location = restaurant.getLocation();
        double dLat = Math.toRadians(location.getLatitude() - latitude);
        double dLng = Math.toRadians(location.getLongitude() - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(location.getLatitude()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static String distancePhrase(double kilometers) {
        if (kilometers < 0) {
            return "";
        }
        if (kilometers < 10) {
            return "About " + String.format(java.util.Locale.ROOT, "%.1f", kilometers) + " km away";
        }
        return "About " + Math.round(kilometers) + " km away";
    }
}
