package foodassistant.agent;

import foodassistant.model.Constraint;
import foodassistant.model.FoodWords;
import foodassistant.model.RecommendationRequest;

import java.util.ArrayList;
import java.util.List;

/** Pulls cuisine, budget, and similar limits out of a sentence. */
public final class ConstraintExtractor {
    public RecommendationRequest extract(String message, double latitude, double longitude) {
        String query = message == null ? "" : message.trim();
        String lower = FoodWords.normalize(query);
        List<String> avoided = avoided(lower);
        List<Constraint> constraints = new ArrayList<>();
        for (String word : avoided) {
            constraints.add(new Constraint(Constraint.AVOID, word, 1));
        }
        for (String word : FoodWords.mentioned(query)) {
            if (contains(avoided, word)) {
                continue;
            }
            String type = dietary(word) ? Constraint.DIETARY : Constraint.CUISINE;
            constraints.add(new Constraint(type, word, dietary(word) ? 1 : 2));
        }
        String budget = budget(lower);
        if (!budget.isEmpty()) {
            constraints.add(new Constraint(Constraint.BUDGET, budget, 1));
        }
        boolean preferDistance = lower.contains("near me")
                || lower.contains("nearby")
                || lower.contains("closest")
                || lower.contains("close to me")
                || lower.contains("walking distance");
        if (preferDistance) {
            constraints.add(new Constraint(Constraint.DISTANCE, "near", 2));
        }
        String occasion = occasion(lower);
        if (!occasion.isEmpty()) {
            constraints.add(new Constraint(Constraint.OCCASION, occasion, 2));
        }
        double maxPrice = switch (budget) {
            case "$" -> 1;
            case "$$" -> 2;
            case "$$$" -> 3;
            case "$$$$" -> 4;
            default -> 0;
        };
        return new RecommendationRequest(query, latitude + ", " + longitude, occasion, maxPrice, constraints, preferDistance);
    }

    private static List<String> avoided(String lower) {
        List<String> avoided = new ArrayList<>();
        String[] leads = {"not ", "no ", "without "};
        for (String lead : leads) {
            int at = 0;
            while ((at = lower.indexOf(lead, at)) >= 0) {
                String rest = lower.substring(at + lead.length());
                for (String word : FoodWords.words()) {
                    if (rest.startsWith(FoodWords.normalize(word))) {
                        if (!contains(avoided, word)) {
                            avoided.add(word);
                        }
                    }
                }
                at += lead.length();
            }
        }
        return avoided;
    }

    private static String budget(String lower) {
        java.util.regex.Matcher money = java.util.regex.Pattern.compile("\\$\\s*(\\d+)").matcher(lower);
        if (money.find()) {
            int dollars = Integer.parseInt(money.group(1));
            if (dollars <= 15) {
                return "$";
            }
            if (dollars <= 30) {
                return "$$";
            }
            if (dollars <= 60) {
                return "$$$";
            }
            return "$$$$";
        }
        if (lower.contains("not expensive")
                || lower.contains("inexpensive")
                || lower.contains("cheap")
                || lower.contains("budget")
                || lower.contains("affordable")) {
            return "$";
        }
        if (lower.contains("fine dining") || lower.contains("upscale") || lower.contains("fancy")) {
            return "$$$";
        }
        if (lower.contains("expensive")) {
            return "$$$";
        }
        return "";
    }

    private static String occasion(String lower) {
        if (lower.contains("date")) {
            return "date";
        }
        if (lower.contains("birthday")) {
            return "birthday";
        }
        if (lower.contains("family")) {
            return "family";
        }
        if (lower.contains("friend")) {
            return "friends";
        }
        if (lower.contains("tonight")) {
            return "tonight";
        }
        if (lower.contains("brunch")) {
            return "brunch";
        }
        if (lower.contains("dinner")) {
            return "dinner";
        }
        if (lower.contains("lunch")) {
            return "lunch";
        }
        return "";
    }

    private static boolean dietary(String word) {
        String normal = FoodWords.normalize(word);
        return normal.equals("vegan")
                || normal.equals("vegetarian")
                || normal.equals("halal")
                || normal.equals("gluten free");
    }

    private static boolean contains(List<String> values, String word) {
        for (String value : values) {
            if (value.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }
}
