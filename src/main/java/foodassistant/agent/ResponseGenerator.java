package foodassistant.agent;

import foodassistant.llm.LLMEngine;
import foodassistant.llm.LocalLLMEngine;
import foodassistant.model.Recommendation;
import foodassistant.model.RecommendationRequest;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.model.UserPreferences;
import foodassistant.taste.MemoryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes the chat reply from places and taste the application has already checked. */
public final class ResponseGenerator {
    private static final String ROLE = """
            You are FoodAgent, a friendly restaurant recommendation assistant.
            Be casual, brief, and natural. Help the person decide where to eat.
            Use only the verified application data below.
            Never invent restaurant names, addresses, ratings, prices, menus, or opening hours.
            A price band is $, $$, $$$, or $$$$. Do not turn a band into a dollar amount.
            If the price was not listed, say that. The application has no menus.
            Do not claim a restaurant serves a dish unless its name, cuisine, or notes support that dish. Otherwise call it a nearby alternative.
            When the task is ask, ask one useful follow-up and name no restaurants.
            When the task is none, say nothing matched and offer to relax the budget, cuisine, or distance.
            When the task is recommend, acknowledge the request, mention their taste when it matters, recommend the verified places, give a short reason from the notes, and end with one follow-up.
            Do not label lines Cuisine, Price, or Reason.
            """;

    private final LLMEngine llm;

    public ResponseGenerator(LLMEngine llm) {
        this.llm = llm;
    }

    public String generate(
            String task,
            Intent intent,
            RecommendationRequest request,
            MemoryService.Snapshot snapshot,
            List<Recommendation> recommendations,
            List<FoodAgent.Turn> history,
            boolean forYou,
            boolean budgetRelaxed) {
        String facts = facts(task, intent, request, snapshot, recommendations, forYou, budgetRelaxed);
        List<Map<String, String>> messages = messages(history, request, forYou);
        try {
            String reply = llm.complete(ROLE + "\n" + facts, messages);
            if (reply != null && !reply.isBlank()) {
                return shorten(reply);
            }
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            System.err.println(message == null || message.isBlank()
                    ? "Language model did not answer; using the local explanation."
                    : message);
        }
        return shorten(new LocalLLMEngine().generateResponse(facts));
    }

    private static List<Map<String, String>> messages(
            List<FoodAgent.Turn> history,
            RecommendationRequest request,
            boolean forYou) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (history != null) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                FoodAgent.Turn turn = history.get(i);
                if (turn.text() == null || turn.text().isBlank()) {
                    continue;
                }
                messages.add(message("assistant".equals(turn.role()) ? "assistant" : "user", turn.text()));
            }
        }
        if (forYou) {
            messages.add(message("user", "For you"));
        } else if (!request.getQuery().isBlank()) {
            messages.add(message("user", request.getQuery()));
        }
        if (messages.isEmpty()) {
            messages.add(message("user", "Help me decide where to eat."));
        }
        return messages;
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String facts(
            String task,
            Intent intent,
            RecommendationRequest request,
            MemoryService.Snapshot snapshot,
            List<Recommendation> recommendations,
            boolean forYou,
            boolean budgetRelaxed) {
        UserPreferences preferences = snapshot.preferences();
        StringBuilder facts = new StringBuilder();
        facts.append("Task: ").append(task).append('\n');
        if (forYou) {
            facts.append("They pressed For you.\n");
        }
        facts.append("They asked: ").append(request.getQuery().isBlank() ? "something that fits your taste" : request.getQuery()).append('\n');
        facts.append("Extracted dish: ").append(blank(intent.dish())).append('\n');
        facts.append("Extracted cuisine: ").append(blank(intent.cuisine())).append('\n');
        facts.append("Extracted budget band: ").append(blank(intent.budget())).append('\n');
        facts.append("Extracted location: ").append(blank(intent.location())).append('\n');
        if (!intent.budget().isBlank()) {
            facts.append(budgetRelaxed
                    ? "Budget filter: too few places had a price in that band, so the search was widened.\n"
                    : "Budget filter: places were requested inside this band. A missing price was not verified.\n");
        }
        facts.append("Menu: not available\n");
        if (!preferences.getDescription().isBlank()) {
            facts.append("About them: ").append(preferences.getDescription()).append('\n');
        }
        facts.append("Interests: ").append(display(preferences.getInterests())).append('\n');
        facts.append("Likes: ").append(display(preferences.getLikes())).append('\n');
        facts.append("Dislikes: ").append(display(preferences.getDislikes())).append('\n');
        if (!preferences.getPriceComfort().isBlank()) {
            facts.append("Budget they usually pick: ").append(preferences.getPriceComfort()).append('\n');
        }
        facts.append("Reviews:\n");
        List<Review> reviews = snapshot.reviews() == null ? List.of() : snapshot.reviews();
        int shown = 0;
        for (Review review : reviews) {
            if (shown == 6) {
                break;
            }
            String note = review.getBody() == null ? "" : review.getBody().trim();
            if (note.length() > 80) {
                note = note.substring(0, 80);
            }
            facts.append("- ")
                    .append(review.getRestaurantName())
                    .append(", ")
                    .append(review.getRating())
                    .append(" stars");
            if (!review.getTags().isEmpty()) {
                facts.append(", tags ").append(String.join(", ", review.getTags()));
            }
            if (!note.isEmpty()) {
                facts.append(". \"").append(note).append('"');
            }
            facts.append('\n');
            shown++;
        }
        if (shown == 0) {
            facts.append("none\n");
        }
        facts.append("Verified places:\n");
        if (recommendations == null || recommendations.isEmpty()) {
            facts.append("none\n");
        } else {
            for (Recommendation recommendation : recommendations) {
                Restaurant restaurant = recommendation.getRestaurant();
                facts.append("- ")
                        .append(restaurant.getName())
                        .append(" | ")
                        .append(restaurant.getCuisine())
                        .append(" | ")
                        .append(restaurant.getPriceRange().isBlank() ? "price unknown" : restaurant.getPriceRange())
                        .append(" | rating ")
                        .append(restaurant.getRating() > 0 ? restaurant.getRating() : "unrated")
                        .append(" | ")
                        .append(recommendation.getExplanation().isBlank() ? "Nearby option" : recommendation.getExplanation())
                        .append(" | ")
                        .append(restaurant.getAddress().isBlank() ? "address unknown" : restaurant.getAddress())
                        .append('\n');
            }
        }
        return facts.toString();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String display(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String shorten(String reply) {
        String trimmed = reply.trim();
        if (trimmed.length() <= 1400) {
            return trimmed;
        }
        return trimmed.substring(0, 1400).trim() + "…";
    }
}
