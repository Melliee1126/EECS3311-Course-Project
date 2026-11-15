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
            Words such as fancy-looking, stylish, aesthetic, elegant, upscale, and beautiful interior are a mood. Leave budget empty unless they name a price, cheap, inexpensive, affordable, or expensive.
            A follow-up such as "what can you find", "anything is fine", "show me alternatives", "you can't find it?", or "yes" keeps the earlier kind of place and area and uses search.
            Do not invent a dish, cuisine, budget, or neighborhood they did not say.
            """;

    private final LLMEngine llm;
    private final ConstraintExtractor rules = new ConstraintExtractor();

    public IntentExtractor(LLMEngine llm) {
        this.llm = llm;
    }

    public Intent extract(String message, List<FoodAgent.Turn> history) {
        String asked = message == null ? "" : message.trim();
        Intent parsed = null;
        try {
            String reply = llm.complete(SYSTEM, List.of(Map.of("role", "user", "content", conversation(asked, history))));
            parsed = parse(reply);
        } catch (RuntimeException ignored) {
            // The word rules below still decide search or ask.
        }
        if (parsed == null) {
            parsed = fromRules(asked, history);
        }
        return RequestSense.ground(parsed, asked, history);
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
        if (RequestSense.reuseEarlier(asked)) {
            String earlier = RequestSense.earlier(history);
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
        if (RequestSense.vague(normal) && dish.isBlank() && cuisine.isBlank() && budget.isBlank()) {
            return Intent.ask();
        }
        if (dish.isBlank() && cuisine.isBlank() && budget.isBlank() && !RequestSense.continues(FoodWords.normalize(asked))) {
            return Intent.ask();
        }
        return new Intent(dish, cuisine, budget, location, "search");
    }

    private static String dishFrom(String text) {
        String normal = FoodWords.normalize(text).replaceAll("[?!.]", "");
        normal = normal.replaceAll(
                "\\b(any|some|a|an|the|restaurants?|places?|that|have|has|with|find|me|show|want|i|something|looking|for|near|nearby|you|it|of|to|do|can|could|please|just|food|eat|hungry|today|what|should|good)\\b",
                " ");
        return normal.replaceAll("\\s+", " ").trim();
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
        if (lower.contains("cheap") || lower.contains("budget") || lower.contains("inexpensive") || lower.contains("affordable")) {
            return "$";
        }
        if (lower.contains("fine dining") || lower.contains("expensive")) {
            return "$$$";
        }
        return "";
    }
}
