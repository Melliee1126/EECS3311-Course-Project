package foodassistant.agent;

import foodassistant.llm.LLMEngine;
import foodassistant.llm.LocalLLMEngine;
import foodassistant.model.FoodWords;
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
            Be casual and natural. Help the person decide where to eat.
            For a recommendation, write one or two sentences about each place.
            Use only the verified application data below.
            Never invent restaurant names, addresses, ratings, prices, menus, or opening hours.
            A price band is $, $$, $$$, or $$$$. Do not turn a band into a dollar amount.
            If the price was not listed, say that. The application has no menus.
            Do not claim a restaurant serves a dish unless its name, cuisine, or notes support that dish. Otherwise call it a nearby alternative.
            When the task is ask, ask one useful follow-up and name no restaurants.
            When the task is none, say which kind of place and area you kept. If the facts say no budget was requested, do not suggest relaxing or changing a budget. Do not suggest changing the cuisine or the neighborhood. Offer to look a little farther only as a last resort, in one natural sentence.
            When the task is recommend, acknowledge what they wanted in one sentence. If an atmosphere is listed, say you treated it as a mood and looked for that kind of place. Never say a place is fancy, stylish, elegant, cozy, aesthetic, upscale, or that it has a certain interior, vibe, or atmosphere. If a follow-up or search note says the search was broadened, say you kept the same kind of place and area. Then explain every verified place in one or two sentences, using only its Personal line. Speak to them directly: name the food they like, whether that like is strong or mild, a review they wrote, or a similar place they saved. Do not open with the star rating, the price, or how far the place is. Mention rating or distance only when the Personal line says nothing they stored matches, and then tie the place to what they asked for. Do not repeat labels such as Highly rated nearby, Nearby option, or Close to you. Do not invent a taste connection that the Personal line does not state. End with one follow-up.
            If they dislike a food, do not recommend a place whose name or cuisine contains it. Every restaurant you name must be one of the verified places.
            If Diet is listed, they follow it. Keep it in mind and mention it in one short clause when you searched with it. If Avoids is listed, those are foods they do not eat, including allergies. Do not suggest a place centered on an avoided food. There are no menus, so do not claim a dish is safe.
            If a Remembered line is present, say in one short clause that you will remember that taste. A diet or an allergy stays until they say it changed. A strong like is not the same as a mild one. Do not treat a one-time craving, or a request to find a place tonight, as a lasting preference.
            Vary the wording. Never use one stock line about relaxing the budget, cuisine, or distance.
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
            boolean budgetRelaxed,
            boolean broadened,
            String latest,
            String remembered) {
        String facts = facts(task, intent, request, snapshot, recommendations, forYou, budgetRelaxed, broadened, latest, remembered);
        List<Map<String, String>> messages = messages(history, request, forYou, latest);
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
            boolean forYou,
            String latest) {
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
        } else if (latest != null && !latest.isBlank()) {
            messages.add(message("user", latest));
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
            boolean budgetRelaxed,
            boolean broadened,
            String latest,
            String remembered) {
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
        if (!intent.style().isBlank()) {
            facts.append("Atmosphere: ").append(intent.style())
                    .append(". This is a look or mood, not a budget and not a cuisine. Do not call any place fancy, stylish, elegant, cozy, or atmospheric, and do not describe its interior.\n");
        }
        if (latest != null && !latest.isBlank() && !latest.equals(request.getQuery())) {
            facts.append("Follow-up: ").append(latest)
                    .append(". This permits a broader search for the same kind of place and the same area. Do not switch cuisine or neighborhood.\n");
        }
        if (broadened) {
            facts.append("Search note: the narrow wording had no matches, so the search kept the kind of place");
            if (!intent.location().isBlank()) {
                facts.append(" and stayed around ").append(intent.location());
            }
            facts.append(". It did not switch cuisine or neighborhood.\n");
        }
        if (("none".equals(task) || "recommend".equals(task)) && intent.budget().isBlank()) {
            facts.append("Budget requested: no. Do not suggest relaxing, raising, or changing a budget.\n");
        }
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
        facts.append("Likes: ").append(preferences.labeled(preferences.getLikes())).append('\n');
        facts.append("Dislikes: ").append(preferences.labeled(preferences.getDislikes())).append('\n');
        facts.append("Diet: ").append(display(preferences.diets())).append('\n');
        facts.append("Avoids: ").append(display(preferences.avoids())).append('\n');
        if (remembered != null && !remembered.isBlank()) {
            facts.append("Remembered: ").append(remembered).append('\n');
        }
        if (!preferences.getPriceComfort().isBlank()) {
            facts.append("Budget they usually pick: ").append(preferences.getPriceComfort());
            if (intent.budget().isBlank()) {
                facts.append(". Background taste only, not a budget they asked for in this message");
            }
            facts.append('\n');
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
            List<Restaurant> saved = snapshot.saved() == null ? List.of() : snapshot.saved();
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
                        .append(restaurant.getAddress().isBlank() ? "address unknown" : restaurant.getAddress())
                        .append('\n');
                facts.append("Personal: ")
                        .append(personal(restaurant, preferences, reviews, saved, intent))
                        .append('\n');
            }
        }
        return facts.toString();
    }

    private static String personal(
            Restaurant restaurant,
            UserPreferences preferences,
            List<Review> reviews,
            List<Restaurant> saved,
            Intent intent) {
        List<String> links = new ArrayList<>();
        boolean taste = false;
        if (!intent.dish().isBlank() && RestaurantMatch.matches(restaurant, intent.dish())) {
            links.add("They asked for " + intent.dish() + ", and this place's name, cuisine, or tags include that word");
            taste = true;
        } else if (!intent.dish().isBlank()) {
            links.add("They asked for " + intent.dish() + ", but this place's name, cuisine, and tags do not include that word, so call it a nearby alternative rather than that dish");
        }
        if (!intent.cuisine().isBlank()
                && !intent.cuisine().equalsIgnoreCase(intent.dish())
                && RestaurantMatch.matches(restaurant, intent.cuisine())) {
            links.add("They asked for " + intent.cuisine() + ", and this place is listed that way");
            taste = true;
        }
        for (String interest : preferences.getInterests()) {
            if (contains(preferences.getDislikes(), interest) || !RestaurantMatch.matches(restaurant, interest)) {
                continue;
            }
            links.add("It lines up with an interest they picked, " + interest);
            taste = true;
        }
        for (String like : preferences.getLikes()) {
            if (!RestaurantMatch.matches(restaurant, like)) {
                continue;
            }
            links.add(likePhrase(like, preferences.strength(like)));
            taste = true;
        }
        for (String diet : preferences.diets()) {
            if (!RestaurantMatch.matches(restaurant, diet)) {
                continue;
            }
            links.add("They follow " + diet + ", and that word is in this place's name, cuisine, or tags");
            taste = true;
        }
        String own = ownReview(restaurant, reviews);
        if (!own.isBlank()) {
            links.add(own);
            taste = true;
        } else {
            String similar = similarReview(restaurant, reviews);
            if (!similar.isBlank()) {
                links.add(similar);
                taste = true;
            }
        }
        String kept = savedNote(restaurant, saved);
        if (!kept.isBlank()) {
            links.add(kept);
            taste = true;
        }
        StringBuilder note = new StringBuilder();
        if (!taste) {
            note.append("Nothing in their interests, likes, reviews, or saved places matches this place's name, cuisine, or tags.");
            if (!links.isEmpty()) {
                note.append(' ').append(String.join(". ", links)).append('.');
            }
        } else {
            note.append(String.join(". ", links)).append('.');
        }
        if (!restaurant.getTags().isEmpty()) {
            List<String> tags = restaurant.getTags().size() <= 5
                    ? restaurant.getTags()
                    : restaurant.getTags().subList(0, 5);
            note.append(" Verified tags: ").append(String.join(", ", tags)).append('.');
        }
        return oneLine(note.toString());
    }

    private static String likePhrase(String like, int strength) {
        String how = strength == 0 ? "like" : UserPreferences.strengthWord(strength);
        if ("like".equals(how)) {
            return "They like " + like;
        }
        return "They have a " + how + " for " + like;
    }

    private static String ownReview(Restaurant restaurant, List<Review> reviews) {
        for (Review review : reviews) {
            if (!samePlace(restaurant, review.getRestId(), review.getRestaurantName())) {
                continue;
            }
            StringBuilder note = new StringBuilder("They already reviewed this place and gave it ");
            note.append(review.getRating()).append(" stars");
            String body = clipQuote(review.getBody());
            if (!body.isBlank()) {
                note.append(". They wrote \"").append(body).append('"');
            }
            if (!review.getTags().isEmpty()) {
                note.append(". Tags they used: ").append(String.join(", ", review.getTags()));
            }
            return note.toString();
        }
        return "";
    }

    private static String similarReview(Restaurant restaurant, List<Review> reviews) {
        for (Review review : reviews) {
            if (samePlace(restaurant, review.getRestId(), review.getRestaurantName())) {
                continue;
            }
            List<String> words = new ArrayList<>(review.getTags());
            words.addAll(FoodWords.mentioned(review.getBody() + " " + review.getRestaurantName()));
            String hit = "";
            for (String word : words) {
                if (RestaurantMatch.matches(restaurant, word)) {
                    hit = word;
                    break;
                }
            }
            if (hit.isBlank()) {
                continue;
            }
            return "They gave " + review.getRestaurantName() + " " + review.getRating()
                    + " stars and brought up " + hit + ", which also fits this place";
        }
        return "";
    }

    private static String savedNote(Restaurant restaurant, List<Restaurant> saved) {
        String sameCuisine = "";
        for (Restaurant other : saved) {
            if (samePlace(restaurant, other.getRestId(), other.getName())) {
                return "This is a place they saved";
            }
            if (!sameCuisine.isBlank() || other.getCuisine().isBlank()) {
                continue;
            }
            if (other.getCuisine().equalsIgnoreCase(restaurant.getCuisine())
                    || RestaurantMatch.matches(restaurant, other.getCuisine())) {
                sameCuisine = "They already saved " + other.getName() + ", which is also " + other.getCuisine();
            }
        }
        return sameCuisine;
    }

    private static boolean samePlace(Restaurant restaurant, String restId, String name) {
        if (restId != null && !restId.isBlank() && restId.equals(restaurant.getRestId())) {
            return true;
        }
        return name != null && !name.isBlank() && name.equalsIgnoreCase(restaurant.getName());
    }

    private static boolean contains(List<String> values, String word) {
        for (String value : values) {
            if (value.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    private static String clipQuote(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = oneLine(body).replace('"', '\'');
        return trimmed.length() <= 90 ? trimmed : trimmed.substring(0, 90).trim();
    }

    private static String oneLine(String text) {
        return text.replace('|', '/').replaceAll("\\s+", " ").trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String display(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String shorten(String reply) {
        String trimmed = reply.trim();
        if (trimmed.length() <= 2400) {
            return trimmed;
        }
        return trimmed.substring(0, 2400).trim() + "…";
    }
}
