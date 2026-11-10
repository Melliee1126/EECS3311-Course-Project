package foodassistant.model;

import java.util.List;

/** The sentence the person asked, plus the limits found in it. */
public final class RecommendationRequest {
    private final String query;
    private final String location;
    private final String occasion;
    private final double maxPrice;
    private final List<Constraint> constraints;
    private final boolean preferDistance;

    public RecommendationRequest(
            String query,
            String location,
            String occasion,
            double maxPrice,
            List<Constraint> constraints,
            boolean preferDistance) {
        this.query = query == null ? "" : query.trim();
        this.location = location == null ? "" : location;
        this.occasion = occasion == null ? "" : occasion;
        this.maxPrice = maxPrice;
        this.constraints = List.copyOf(constraints == null ? List.of() : constraints);
        this.preferDistance = preferDistance;
    }

    public String getQuery() {
        return query;
    }

    public String getLocation() {
        return location;
    }

    public String getOccasion() {
        return occasion;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public List<Constraint> getConstraints() {
        return constraints;
    }

    public boolean prefersDistance() {
        return preferDistance;
    }
}
