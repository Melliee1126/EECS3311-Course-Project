package foodassistant.agent;

import foodassistant.model.UserPreferences;

/** What a ranking strategy is allowed to look at. */
public final class RankContext {
    private final UserPreferences preferences;
    private final double latitude;
    private final double longitude;

    public RankContext(UserPreferences preferences, double latitude, double longitude) {
        this.preferences = preferences == null ? UserPreferences.empty() : preferences;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UserPreferences preferences() {
        return preferences;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }
}
