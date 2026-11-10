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
        List<String> places = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Task:")) {
                task = trimmed.substring("Task:".length()).trim();
            } else if (trimmed.startsWith("They asked:")) {
                asked = trimmed.substring("They asked:".length()).trim();
            } else if (trimmed.startsWith("Interests:")) {
                interests = trimmed.substring("Interests:".length()).trim();
            } else if (trimmed.startsWith("Likes:") && (interests.isBlank() || interests.equals("none"))) {
                interests = trimmed.substring("Likes:".length()).trim();
            } else if (trimmed.startsWith("- ") && trimmed.contains("|")) {
                places.add(trimmed.substring(2));
            }
        }
        if ("ask".equals(task)) {
            if (asked.toLowerCase(java.util.Locale.ROOT).contains("new") || asked.toLowerCase(java.util.Locale.ROOT).contains("tried")) {
                return "Let's try something new. You've already covered a few favorites. Want me to look up a different cuisine nearby?";
            }
            if (!interests.isBlank() && !interests.equals("none")) {
                return "Let's find you something good. From what you've saved, you seem to enjoy " + interests
                        + ". Want me to start there, or should I surprise you?";
            }
            return "Let's find you something good. Are you in the mood for something specific, or should I surprise you?";
        }
        if (places.isEmpty() || "none".equals(task)) {
            return "I couldn't find many places that match all of that.\n\n"
                    + "Want me to raise the budget a little, try another cuisine, or look a bit farther?";
        }
        StringBuilder reply = new StringBuilder();
        if (!asked.isBlank() && !asked.equalsIgnoreCase("something that fits your taste")) {
            reply.append("That sounds good. Here are a few places you might like:\n\n");
        } else {
            reply.append("Based on your taste, I'd start with these:\n\n");
        }
        int count = Math.min(3, places.size());
        for (int i = 0; i < count; i++) {
            String[] parts = places.get(i).split("\\|");
            String name = parts[0].trim();
            String cuisine = parts.length > 1 ? parts[1].trim() : "Restaurant";
            String price = parts.length > 2 ? parts[2].trim() : "";
            String reason = parts.length > 4 ? parts[4].trim() : "A solid option nearby";
            reply.append(i + 1).append(". ").append(name).append('\n');
            reply.append(cuisine);
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
