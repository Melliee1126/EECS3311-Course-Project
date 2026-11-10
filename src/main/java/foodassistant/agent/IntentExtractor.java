package foodassistant.agent;

import foodassistant.llm.LLMEngine;
import foodassistant.model.Constraint;
import foodassistant.model.FoodWords;
import foodassistant.model.RecommendationRequest;
import foodassistant.server.Json;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Asks the model what the person wants, then falls back to the word rules. */
public final class IntentExtractor {
    private static final String SYSTEM = """
            You extract a restaurant request. Reply with one JSON object and no markdown.
            Keys:
            dish: the specific food they named, or ""
            cuisine: the cuisine or food style, or ""
            budget: "$", "$$", "$$$", "$$$$", or ""
            location: a neighborhood, city, or "near me", or ""
            intent: "search" or "ask"
            Use search when they name a dish, cuisine, restaurant, budget, or ask for places.
            Use ask when the request is vague, such as "I'm hungry" or "somewhere nice".
            A short follow-up such as "you can't find it?" or "yes" keeps the earlier dish and uses search.
            Do not invent a dish they did not say.
            """;

    private final LLMEngine llm;
    private final ConstraintExtractor rules = new ConstraintExtractor();

    public IntentExtractor(LLMEngine llm) {
        this.llm = llm;
    }

    public Intent extract(String message, List<FoodAgent.Turn> history) {
        String asked = message == null ? "" : message.trim();
        try {
            String reply = llm.complete(SYSTEM, List.of(Map.of("role", "user", "content", conversation(asked, history))));
            Intent parsed = parse(reply);
            if (parsed != null) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
            // The word rules below still decide search or ask.
        }
        return fromRules(asked, history);
    }

    private static String conversation(String asked, List<FoodAgent.Turn> history) {
        StringBuilder text = new StringBuilder();
        if (history != null) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                FoodAgent.Turn turn = history.get(i);
                text.append("assistant".equals(turn.role()) ? "Assistant: " : "User: ");
                text.append(turn.text()).append('\n');
            }
        }
        text.append("Latest: ").append(asked);
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private static Intent parse(String reply) {
        if (reply == null) {
            return null;
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        Object parsed = Json.parse(reply.substring(start, end + 1));
        if (!(parsed instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> json = (Map<String, Object>) raw;
        String dish = text(json, "dish");
        String cuisine = text(json, "cuisine");
        String budget = budget(text(json, "budget"));
        String location = text(json, "location");
        String intent = text(json, "intent").toLowerCase(Locale.ROOT);
        if (!"search".equals(intent) && !"ask".equals(intent)) {
            intent = dish.isBlank() && cuisine.isBlank() && budget.isBlank() ? "ask" : "search";
        }
        return new Intent(dish, cuisine, budget, location, intent);
    }

    private Intent fromRules(String asked, List<FoodAgent.Turn> history) {
        String normal = FoodWords.normalize(asked);
        String source = asked;
        if (continues(normal) || affirmation(normal)) {
            String earlier = previousRequest(history);
            if (!earlier.isBlank()) {
                source = earlier;
                normal = FoodWords.normalize(source);
            }
        }
        RecommendationRequest extracted = rules.extract(source, 0, 0);
        String budget = "";
        String cuisine = String.join(" ", FoodWords.mentioned(source));
        for (Constraint constraint : extracted.getConstraints()) {
            if (Constraint.BUDGET.equals(constraint.getType())) {
                budget = constraint.getValue();
            }
        }
        String location = extracted.prefersDistance() ? "near me" : "";
        if (normal.contains("surprise")) {
            return new Intent("", cuisine, budget, location.isBlank() ? "near me" : location, "search");
        }
        String dish = dishFrom(source);
        if (vague(normal) && dish.isBlank() && cuisine.isBlank() && budget.isBlank()) {
            return Intent.ask();
        }
        if (dish.isBlank() && cuisine.isBlank() && budget.isBlank() && !continues(FoodWords.normalize(asked))) {
            return Intent.ask();
        }
        return new Intent(dish, cuisine, budget, location, "search");
    }

    private static String previousRequest(List<FoodAgent.Turn> history) {
        if (history == null) {
            return "";
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            FoodAgent.Turn turn = history.get(i);
            if (!"user".equals(turn.role()) || turn.text() == null) {
                continue;
            }
            String normal = FoodWords.normalize(turn.text());
            if (vague(normal) || continues(normal) || affirmation(normal)) {
                continue;
            }
            return turn.text().trim();
        }
        return "";
    }

    private static String dishFrom(String text) {
        String normal = FoodWords.normalize(text).replaceAll("[?!.]", "");
        normal = normal.replaceAll(
                "\\b(any|some|a|an|the|restaurants?|places?|that|have|has|with|find|me|show|want|i|something|looking|for|near|nearby|you|it|of|to|do|can|could|please|just|food|eat|hungry|today|what|should|good)\\b",
                " ");
        return normal.replaceAll("\\s+", " ").trim();
    }

    private static boolean vague(String normal) {
        return normal.isBlank()
                || normal.contains("i'm hungry")
                || normal.contains("im hungry")
                || normal.contains("i am hungry")
                || normal.contains("what should i eat")
                || normal.contains("somewhere nice")
                || normal.contains("something nice")
                || normal.contains("not sure");
    }

    private static boolean continues(String normal) {
        return normal.contains("can't find")
                || normal.contains("cant find")
                || normal.contains("cannot find")
                || normal.contains("try again")
                || normal.contains("look again")
                || normal.contains("search again");
    }

    private static boolean affirmation(String normal) {
        return normal.equals("yes")
                || normal.equals("yeah")
                || normal.equals("yep")
                || normal.equals("sure")
                || normal.equals("ok")
                || normal.equals("okay")
                || normal.equals("please")
                || normal.equals("go ahead");
    }

    private static String text(Map<String, Object> json, String key) {
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                return String.valueOf(entry.getValue()).trim();
            }
        }
        return "";
    }

    private static String budget(String value) {
        String lower = FoodWords.normalize(value);
        if (lower.matches("\\$+")) {
            return lower.length() > 4 ? "$$$$" : lower;
        }
        Matcher money = Pattern.compile("(\\d+)").matcher(lower);
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
        if (lower.contains("cheap") || lower.contains("budget") || lower.contains("inexpensive")) {
            return "$";
        }
        if (lower.contains("fine") || lower.contains("fancy") || lower.contains("expensive")) {
            return "$$$";
        }
        return "";
    }
}
