package foodassistant.agent;

import foodassistant.model.Constraint;
import foodassistant.model.Restaurant;

import java.util.ArrayList;
import java.util.List;

/** Drops restaurants that break a hard limit, and relaxes soft limits if nothing remains. */
public final class CandidatesFilter {
    public List<Restaurant> filter(List<Restaurant> restaurants, List<Constraint> constraints) {
        List<Constraint> limits = constraints == null ? List.of() : constraints;
        List<Restaurant> matched = apply(restaurants, limits);
        if (!matched.isEmpty()) {
            return matched;
        }
        if (has(limits, Constraint.CUISINE) || has(limits, Constraint.OCCASION)) {
            matched = apply(restaurants, lowerPriority(limits));
        }
        if (matched.isEmpty() && has(limits, Constraint.DIETARY)) {
            matched = apply(restaurants, drop(lowerPriority(limits), Constraint.DIETARY));
        }
        return matched;
    }

    public List<Constraint> lowerPriority(List<Constraint> constraints) {
        return drop(constraints, Constraint.CUISINE, Constraint.OCCASION);
    }

    private static List<Restaurant> apply(List<Restaurant> restaurants, List<Constraint> constraints) {
        List<Constraint> cuisines = ofType(constraints, Constraint.CUISINE);
        List<Constraint> required = new ArrayList<>();
        for (Constraint constraint : constraints) {
            if (!Constraint.CUISINE.equals(constraint.getType())
                    && !Constraint.DISTANCE.equals(constraint.getType())
                    && !Constraint.OCCASION.equals(constraint.getType())) {
                required.add(constraint);
            }
        }
        List<Restaurant> matched = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            if (!satisfiesAll(restaurant, required)) {
                continue;
            }
            if (!cuisines.isEmpty() && !satisfiesAny(restaurant, cuisines)) {
                continue;
            }
            matched.add(restaurant);
        }
        return matched;
    }

    private static boolean satisfiesAll(Restaurant restaurant, List<Constraint> constraints) {
        for (Constraint constraint : constraints) {
            if (!constraint.isSatisfiedBy(restaurant)) {
                return false;
            }
        }
        return true;
    }

    private static boolean satisfiesAny(Restaurant restaurant, List<Constraint> constraints) {
        for (Constraint constraint : constraints) {
            if (constraint.isSatisfiedBy(restaurant)) {
                return true;
            }
        }
        return false;
    }

    private static List<Constraint> ofType(List<Constraint> constraints, String type) {
        List<Constraint> matched = new ArrayList<>();
        for (Constraint constraint : constraints) {
            if (type.equals(constraint.getType())) {
                matched.add(constraint);
            }
        }
        return matched;
    }

    private static boolean has(List<Constraint> constraints, String type) {
        return !ofType(constraints, type).isEmpty();
    }

    private static List<Constraint> drop(List<Constraint> constraints, String... types) {
        List<Constraint> kept = new ArrayList<>();
        for (Constraint constraint : constraints) {
            boolean remove = false;
            for (String type : types) {
                if (type.equals(constraint.getType())) {
                    remove = true;
                    break;
                }
            }
            if (!remove) {
                kept.add(constraint);
            }
        }
        return kept;
    }
}
