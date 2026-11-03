package foodassistant.map;

import foodassistant.model.Restaurant;
import foodassistant.repository.RestaurantRepository;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public final class MapService {
    private final RestaurantRepository restaurantRepository;
    private final MapProvider mapProvider;
    private final PrintStream out;

    public MapService(RestaurantRepository restaurantRepository, MapProvider mapProvider, PrintStream out) {
        this.restaurantRepository = restaurantRepository;
        this.mapProvider = mapProvider;
        this.out = out;
    }

    public List<Restaurant> loadMap(String query, String cuisine, double latitude, double longitude, double radiusKm) {
        List<Restaurant> matches = restaurantRepository.findByFilters(query, cuisine);
        List<Restaurant> nearby = new ArrayList<>();
        for (Restaurant restaurant : restaurantRepository.findByLocation(latitude, longitude, radiusKm)) {
            if (matches.contains(restaurant)) {
                nearby.add(restaurant);
            }
        }
        List<Restaurant> pins = restaurantRepository.withLocation(nearby);
        out.println("Loaded " + matches.size() + " restaurant(s); " + pins.size() + " can be pinned.");
        if (mapProvider.isAvailable()) {
            mapProvider.showMarkers(pins);
        } else {
            out.println("Map service unavailable. Showing a list instead.");
            printList(matches);
        }
        printMissingLocations(matches);
        return matches;
    }

    public void addMarkers(List<Restaurant> restaurants) {
        mapProvider.showMarkers(restaurantRepository.withLocation(restaurants));
    }

    private void printList(List<Restaurant> restaurants) {
        if (restaurants.isEmpty()) {
            out.println("No restaurants match the selected filters.");
            return;
        }
        for (Restaurant restaurant : restaurants) {
            out.println("  - " + restaurant.getDetail());
        }
    }

    private void printMissingLocations(List<Restaurant> restaurants) {
        for (Restaurant restaurant : restaurants) {
            if (!restaurant.hasLocation()) {
                out.println("No map pin for " + restaurant.getName() + ". " + restaurant.getDetail());
            }
        }
    }
}
