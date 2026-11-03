package foodassistant.model;

public final class Location {
    private final double latitude;
    private final double longitude;
    private final String address;

    public Location(double latitude, double longitude, String address) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public String getCoordinates() {
        return latitude + ", " + longitude;
    }
}
