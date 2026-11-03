package foodassistant.map;

import foodassistant.model.Restaurant;

import java.util.ArrayList;
import java.util.List;

public final class MapProviderAdapter implements MapProvider {
    private final ExternalMapClient client;

    public MapProviderAdapter(ExternalMapClient client) {
        this.client = client;
    }

    @Override
    public boolean isAvailable() {
        return client.ping();
    }

    @Override
    public void showMarkers(List<Restaurant> restaurants) {
        List<String> labels = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            LocationLabel label = new LocationLabel(restaurant);
            labels.add(label.text());
        }
        client.renderPins(labels);
    }

    private record LocationLabel(Restaurant restaurant) {
        String text() {
            return restaurant.pinLabel() + " @ " + restaurant.getLocation().getCoordinates();
        }
    }
}
