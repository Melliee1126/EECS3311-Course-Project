package foodassistant.map;

import foodassistant.model.Restaurant;

import java.util.List;

public interface MapProvider {
    boolean isAvailable();

    void showMarkers(List<Restaurant> restaurants);
}
