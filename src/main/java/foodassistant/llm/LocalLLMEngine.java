package foodassistant.llm;

import foodassistant.model.FoodWords;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A short explanation written here when no cloud model is configured. */
public final class LocalLLMEngine implements LLMEngine {
    @Override
    public String generateResponse(String prompt) {
        String text = prompt == null ? "" : prompt;
        String task = "recommend";
        String asked = "";
        String interests = "";
        String cuisine = "none";
        String location = "none";
        String budget = "none";
        boolean atmosphere = false;
        boolean followUp = false;
        boolean broadened = false;
        boolean budgetRequested = true;
        String remembered = "";
        List<String> places = new ArrayList<>();
        List<String> personals = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Task:")) {
                task = trimmed.substring("Task:".length()).trim();
            } else if (trimmed.startsWith("They asked:")) {
                asked = trimmed.substring("They asked:".length()).trim();
            } else if (trimmed.startsWith("Extracted cuisine:")) {
                cuisine = trimmed.substring("Extracted cuisine:".length()).trim();
            } else if (trimmed.startsWith("Extracted location:")) {
                location = trimmed.substring("Extracted location:".length()).trim();
            } else if (trimmed.startsWith("Extracted budget band:")) {
                budget = trimmed.substring("Extracted budget band:".length()).trim();
            } else if (trimmed.startsWith("Atmosphere:")) {
                atmosphere = true;
            } else if (trimmed.startsWith("Follow-up:")) {
                followUp = true;
            } else if (trimmed.startsWith("Search note:")) {
                broadened = true;
            } else if (trimmed.startsWith("Budget requested: no")) {
                budgetRequested = false;
            } else if (trimmed.startsWith("Remembered:")) {
                remembered = trimmed.substring("Remembered:".length()).trim();
            } else if (trimmed.startsWith("Interests:")) {
                interests = trimmed.substring("Interests:".length()).trim();
            } else if (trimmed.startsWith("Likes:") && (interests.isBlank() || interests.equals("none"))) {
                interests = trimmed.substring("Likes:".length()).trim();
            } else if (trimmed.startsWith("- ") && trimmed.contains("|")) {
                places.add(trimmed.substring(2));
            } else if (trimmed.startsWith("Personal:") && personals.size() < places.size()) {
                personals.add(trimmed.substring("Personal:".length()).trim());
            }
        }
        if ("ask".equals(task)) {
            if (!remembered.isBlank()) {
                return "I'll remember that: " + remembered + ". Want me to look for something else nearby?";
            }
            if (asked.toLowerCase(java.util.Locale.ROOT).contains("new") || asked.toLowerCase(java.util.Locale.ROOT).contains("tried")) {
                return "Let's try something new. You've already covered a few favorites. Want me to look up a different cuisine nearby?";
            }
            if (!interests.isBlank() && !interests.equals("none")) {
                return "Let's find you something good. From what you've saved, you seem to enjoy " + interests
                        + ". Want me to start there, or should I surprise you?";
            }
            return "Let's find you something good. Are you in the mood for something specific, or should I surprise you?";
        }
        String kind = "none".equalsIgnoreCase(cuisine) || cuisine.isBlank() ? "places" : cuisine.toLowerCase(java.util.Locale.ROOT);
        String kindPhrase = "places".equals(kind) ? "places" : "a " + kind;
        String where = "none".equalsIgnoreCase(location) || location.isBlank() ? "nearby" : location;
        if (places.isEmpty() || "none".equals(task)) {
            String missed = budgetRequested && !"none".equalsIgnoreCase(budget) && !budget.isBlank()
                    ? "I couldn't find " + kindPhrase + " around " + where + " in the price range you named. I can keep that same kind of place and look a little farther."
                    : "I looked for " + kindPhrase + " around " + where + " and didn't get a match with that wording. I can keep looking for " + kindPhrase + " in that area.";
            return remembered.isBlank() ? missed : "I'll remember that: " + remembered + "\n\n" + missed;
        }
        StringBuilder reply = new StringBuilder();
        if (!remembered.isBlank()) {
            reply.append("I'll remember that: ").append(remembered).append(".\n\n");
        }
        if (followUp || broadened) {
            reply.append("I'll stay with ").append(kindPhrase).append(" around ").append(where)
                    .append(" and open the search up a bit, without switching the kind of place or the area.\n\n");
        } else if (atmosphere) {
            reply.append("I can't check the decor from here, so I looked for ").append(kindPhrase).append(" around ").append(where)
                    .append(" and leaned toward well-rated spots.\n\n");
        } else if (!asked.isBlank() && !asked.equalsIgnoreCase("something that fits your taste")) {
            reply.append("That sounds good. Here are a few places you might like:\n\n");
        } else {
            reply.append("Based on your taste, I'd start with these:\n\n");
        }
        int count = Math.min(3, places.size());
        for (int i = 0; i < count; i++) {
            String[] parts = places.get(i).split("\\|");
            String name = parts[0].trim();
            String placeCuisine = parts.length > 1 ? parts[1].trim() : "Restaurant";
            String price = parts.length > 2 ? parts[2].trim() : "";
            String reason = i < personals.size() && !personals.get(i).isBlank()
                    ? personals.get(i)
                    : "A place that fits what you asked for";
            reply.append(i + 1).append(". ").append(name).append('\n');
            reply.append(placeCuisine);
            if (!price.isBlank()) {
                reply.append(" · ").append(price);
            }
            reply.append('\n').append(reason).append("\n\n");
        }
        reply.append("Want me to narrow these by distance?");
        return reply.toString().trim();
    }

    @Override
    public String reason(String context) {
        return generateResponse(context);
    }

    @Override
    public String complete(String system, List<Map<String, String>> messages) {
        StringBuilder text = new StringBuilder();
        if (system != null && !system.isBlank()) {
            text.append(system).append('\n');
        }
        if (messages != null) {
            for (Map<String, String> message : messages) {
                text.append(message.getOrDefault("role", "user")).append(": ");
                text.append(message.getOrDefault("content", "")).append('\n');
            }
        }
        return generateResponse(text.toString());
    }

    @Override
    public List<String> generateTags(String text) {
        return FoodWords.mentioned(text == null ? "" : text);
    }
}
