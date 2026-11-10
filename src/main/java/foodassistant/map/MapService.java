package foodassistant.map;

import foodassistant.model.Restaurant;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public final class MapService {
    private final MapProvider mapProvider;
    private final PrintStream out;

    public MapService(MapProvider mapProvider, PrintStream out) {
        this.mapProvider = mapProvider;
        this.out = out;
    }

    public void show(List<Restaurant> restaurants) {
        if (restaurants.isEmpty()) {
            out.println("No restaurants match the selected filters.");
            return;
        }
        List<Restaurant> pins = withLocation(restaurants);
        out.println("Loaded " + restaurants.size() + " restaurant(s); " + pins.size() + " can be pinned.");
        if (mapProvider.isAvailable()) {
            mapProvider.showMarkers(pins);
        } else {
            out.println("Map service unavailable. Showing a list instead.");
            printList(restaurants);
        }
        printMissingLocations(restaurants);
    }

    private static List<Restaurant> withLocation(List<Restaurant> restaurants) {
        List<Restaurant> located = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            if (restaurant.hasLocation()) {
                located.add(restaurant);
            }
        }
        return located;
    }

    private void printList(List<Restaurant> restaurants) {
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
