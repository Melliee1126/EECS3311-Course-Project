package foodassistant.agent;

import foodassistant.llm.LLMEngine;
import foodassistant.llm.LLMFactory;
import foodassistant.llm.LocalLLMEngine;
import foodassistant.model.Constraint;
import foodassistant.model.FoodWords;
import foodassistant.model.Recommendation;
import foodassistant.model.RecommendationRequest;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.model.UserPreferences;
import foodassistant.places.PlacesSearch;
import foodassistant.server.Json;
import foodassistant.taste.MemoryService;
import foodassistant.taste.TasteProfileUpdater;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade for one recommendation. The page and the command line call this,
 * and it runs extract, search, filter, rank, and explain.
 */
public final class FoodAgent {
    private final PlacesSearch places;
    private final MemoryService memory;
    private final LLMEngine llm;
    private final ConstraintExtractor extractor = new ConstraintExtractor();
    private final CandidatesFilter candidatesFilter = new CandidatesFilter();
    private final RankingStrategy tasteRanking = new TasteBasedRanking();
    private final RankingStrategy distanceRanking = new DistanceBasedRanking();

    public FoodAgent(PlacesSearch places, MemoryService memory, LLMFactory llmFactory) {
        this.places = places;
        this.memory = memory;
        this.llm = llmFactory.createLLM();
    }

    public Answer processRequest(
            String accountId,
            String message,
            List<String> chips,
            List<Turn> history,
            double latitude,
            double longitude,
            boolean forYou) {
        String asked = join(message, chips);
        if (!forYou && asked.isBlank()) {
            throw new IllegalArgumentException("Ask for a kind of food, or use For you.");
        }
        if (asked.length() > 500) {
            throw new IllegalArgumentException("Use 500 characters or fewer.");
        }
        List<Turn> earlier = history == null ? List.of() : history;
        RecommendationRequest latest = extractor.extract(asked, latitude, longitude);
        boolean namedFood = readyToSearch(asked, latest);
        boolean search = forYou || namedFood || (!wantsNew(asked) && (affirmation(asked) || refines(asked)));
        String focus = search ? focus(asked, earlier) : asked;
        RecommendationRequest request = extractor.extract(focus, latitude, longitude);
        MemoryService.Snapshot snapshot = memory.remember(accountId);
        if (!search) {
            String reply = speak("ask", request, snapshot, List.of(), earlier, false);
            return new Answer(reply, List.of(), snapshot.preferences());
        }
        return getRecommendations(request, latitude, longitude, forYou, snapshot, earlier);
    }

    public Answer getRecommendations(
            RecommendationRequest request,
            double latitude,
            double longitude,
            boolean forYou,
            MemoryService.Snapshot snapshot,
            List<Turn> history) {
        UserPreferences taste = snapshot.preferences();
        UserPreferences rankingTaste = TasteProfileUpdater.overlay(taste, snapshot.saved());
        Map<String, String> photos = new LinkedHashMap<>();
        List<Restaurant> found = retrieveRestaurants(request, rankingTaste, latitude, longitude, forYou, photos);
        List<Restaurant> filtered = candidatesFilter.filter(found, request.getConstraints());
        if (filtered.isEmpty()) {
            String reply = speak("none", request, snapshot, List.of(), history, forYou);
            return new Answer(reply, List.of(), taste);
        }
        List<Recommendation> ranked = rankRestaurants(filtered, request, rankingTaste, latitude, longitude, forYou, photos);
        int keep = Math.min(3, ranked.size());
        List<Recommendation> top = List.copyOf(ranked.subList(0, keep));
        String reply = speak("recommend", request, snapshot, top, history, forYou);
        return new Answer(reply, top, taste);
    }

    private List<Restaurant> retrieveRestaurants(
            RecommendationRequest request,
            UserPreferences preferences,
            double latitude,
            double longitude,
            boolean forYou,
            Map<String, String> photos) {
        RuntimeException searchProblem = null;
        List<PlacesSearch.Hit> hits = List.of();
        try {
            hits = places.search(searchText(request, preferences, forYou), latitude, longitude);
        } catch (RuntimeException exception) {
            searchProblem = exception;
        }
        Map<String, Restaurant> byId = new LinkedHashMap<>();
        for (PlacesSearch.Hit hit : hits) {
            Restaurant restaurant = hit.restaurant();
            byId.put(restaurant.getRestId(), restaurant);
            photos.put(restaurant.getRestId(), photoPath(hit.photoName()));
        }
        if (byId.isEmpty() && searchProblem != null) {
            throw searchProblem;
        }
        return new ArrayList<>(byId.values());
    }

    public List<Recommendation> rankRestaurants(
            List<Restaurant> restaurants,
            RecommendationRequest request,
            UserPreferences preferences,
            double latitude,
            double longitude,
            boolean forYou,
            Map<String, String> photos) {
        boolean askedForFood = false;
        for (Constraint constraint : request.getConstraints()) {
            if (Constraint.CUISINE.equals(constraint.getType()) || Constraint.DIETARY.equals(constraint.getType())) {
                askedForFood = true;
                break;
            }
        }
        RankingStrategy strategy = !forYou && request.prefersDistance() && !askedForFood ? distanceRanking : tasteRanking;
        List<Recommendation> ranked = strategy.rank(restaurants, new RankContext(preferences, latitude, longitude));
        List<Recommendation> withPhotos = new ArrayList<>();
        for (Recommendation recommendation : ranked) {
            String photo = photos.getOrDefault(recommendation.getRestaurant().getRestId(), "");
            withPhotos.add(recommendation.withPhoto(photo));
        }
        return withPhotos;
    }

    public String explainRecommendations(
            RecommendationRequest request,
            UserPreferences preferences,
            List<Recommendation> recommendations,
            boolean forYou) {
        MemoryService.Snapshot snapshot = new MemoryService.Snapshot(preferences, List.of(), List.of());
        return speak(recommendations.isEmpty() ? "none" : "recommend", request, snapshot, recommendations, List.of(), forYou);
    }

    private String speak(
            String task,
            RecommendationRequest request,
            MemoryService.Snapshot snapshot,
            List<Recommendation> recommendations,
            List<Turn> history,
            boolean forYou) {
        String prompt = prompt(task, request, snapshot, recommendations, history, forYou);
        try {
            String reply = llm.reason(prompt);
            if (reply != null && !reply.isBlank()) {
                return shorten(reply);
            }
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            System.err.println(message == null || message.isBlank()
                    ? "Language model did not answer; using the local explanation."
                    : message);
        }
        return shorten(new LocalLLMEngine().reason(prompt));
    }

    private static String searchText(RecommendationRequest request, UserPreferences preferences, boolean forYou) {
        if (!forYou) {
            List<String> foods = FoodWords.mentioned(request.getQuery());
            if (!foods.isEmpty()) {
                return String.join(" ", foods) + " restaurants";
            }
        }
        if (!preferences.getInterests().isEmpty()) {
            int count = Math.min(3, preferences.getInterests().size());
            return String.join(" ", preferences.getInterests().subList(0, count)) + " restaurants";
        }
        if (!preferences.getLikes().isEmpty()) {
            return preferences.getLikes().get(0) + " restaurants";
        }
        return "restaurants";
    }

    private static String prompt(
            String task,
            RecommendationRequest request,
            MemoryService.Snapshot snapshot,
            List<Recommendation> recommendations,
            List<Turn> history,
            boolean forYou) {
        UserPreferences preferences = snapshot.preferences();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: ").append(task).append('\n');
        prompt.append("You are a friendly friend helping someone decide where to eat. You are not a search engine.\n");
        prompt.append("Be casual, honest, and short enough to read in a chat. Plain text only. No markdown, no links, and no invented restaurants, prices, or ratings.\n");
        if ("ask".equals(task)) {
            prompt.append("Do not list restaurants. Ask one useful follow-up.\n");
            prompt.append("If their reviews or interests show a taste, mention it and ask whether to start there or surprise them.\n");
            prompt.append("If the request is vague, ask what the occasion is or what kind of food they want.\n");
            prompt.append("If they want something they have not tried, suggest one or two cuisines that are not in their reviews and ask if you should look those up.\n");
        } else if ("none".equals(task)) {
            prompt.append("Nothing matched. Say that plainly and offer to raise the budget, try another cuisine, or look a little farther. Ask which they prefer.\n");
        } else {
            prompt.append("Use only the places listed. Acknowledge what they asked, mention their taste when it matters, then recommend those places.\n");
            prompt.append("For each place, put the name on its own line, then cuisine, price, and one reason. End with one follow-up question.\n");
        }
        if (forYou) {
            prompt.append("They pressed For you, so suggest from their taste.\n");
        }
        if (history != null && !history.isEmpty()) {
            prompt.append("\nConversation so far:\n");
            for (Turn turn : history) {
                prompt.append("user".equals(turn.role()) ? "User: " : "Assistant: ");
                prompt.append(turn.text()).append('\n');
            }
        }
        String asked = request.getQuery().isBlank() ? "something that fits your taste" : request.getQuery();
        prompt.append("\nThey asked: ").append(asked).append('\n');
        if (!preferences.getDescription().isBlank()) {
            prompt.append("About them: ").append(preferences.getDescription()).append('\n');
        }
        prompt.append("Interests: ").append(display(preferences.getInterests())).append('\n');
        prompt.append("Likes: ").append(display(preferences.getLikes())).append('\n');
        prompt.append("Dislikes: ").append(display(preferences.getDislikes())).append('\n');
        if (!preferences.getPriceComfort().isBlank()) {
            prompt.append("Budget they usually pick: ").append(preferences.getPriceComfort()).append('\n');
        }
        if (!request.getOccasion().isBlank()) {
            prompt.append("Occasion: ").append(request.getOccasion()).append('\n');
        }
        prompt.append("Reviews:\n");
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
            prompt.append("- ")
                    .append(review.getRestaurantName())
                    .append(", ")
                    .append(review.getRating())
                    .append(" stars, tags ")
                    .append(review.getTags().isEmpty() ? "none" : String.join(", ", review.getTags()));
            if (!note.isEmpty()) {
                prompt.append(". \"").append(note).append('"');
            }
            prompt.append('\n');
            shown++;
        }
        if (shown == 0) {
            prompt.append("none\n");
        }
        prompt.append("Places:\n");
        if (recommendations == null || recommendations.isEmpty()) {
            prompt.append("none\n");
        }
        for (Recommendation recommendation : recommendations) {
            Restaurant restaurant = recommendation.getRestaurant();
            prompt.append("- ")
                    .append(restaurant.getName())
                    .append(" | ")
                    .append(restaurant.getCuisine())
                    .append(" | ")
                    .append(restaurant.getPriceRange().isBlank() ? "price unknown" : restaurant.getPriceRange())
                    .append(" | rating ")
                    .append(restaurant.getRating() > 0 ? restaurant.getRating() : "unrated")
                    .append(" | ")
                    .append(recommendation.getExplanation())
                    .append('\n');
        }
        return prompt.toString();
    }

    private static boolean readyToSearch(String latest, RecommendationRequest request) {
        String normal = FoodWords.normalize(latest);
        if (containsAny(normal, "surprise me", "surprise", "show me", "recommend")) {
            return true;
        }
        for (Constraint constraint : request.getConstraints()) {
            String type = constraint.getType();
            if (Constraint.CUISINE.equals(type) || Constraint.DIETARY.equals(type) || Constraint.BUDGET.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static String focus(String asked, List<Turn> history) {
        if (history.isEmpty() || (!affirmation(asked) && !refines(asked))) {
            return asked;
        }
        StringBuilder earlier = new StringBuilder();
        for (Turn turn : history) {
            if ("user".equals(turn.role())) {
                if (!earlier.isEmpty()) {
                    earlier.append(' ');
                }
                earlier.append(turn.text());
            }
        }
        if (earlier.isEmpty()) {
            return asked;
        }
        return earlier + " " + asked;
    }

    private static boolean affirmation(String text) {
        String normal = FoodWords.normalize(text);
        return normal.equals("yes")
                || normal.equals("yeah")
                || normal.equals("yep")
                || normal.equals("sure")
                || normal.equals("ok")
                || normal.equals("okay")
                || normal.equals("start there")
                || normal.equals("go ahead")
                || normal.equals("please");
    }

    private static boolean wantsNew(String text) {
        String normal = FoodWords.normalize(text);
        return normal.contains("something new")
                || normal.contains("somewhere new")
                || normal.contains("haven't tried")
                || normal.contains("have not tried")
                || normal.contains("not tried")
                || normal.contains("tried before");
    }

    private static boolean refines(String text) {
        String normal = FoodWords.normalize(text);
        return normal.contains("closer")
                || normal.contains("nearer")
                || normal.contains("cheaper")
                || normal.contains("less expensive")
                || normal.contains("somewhere new")
                || normal.contains("something new");
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    public static String response(Answer answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Recommendation recommendation : answer.recommendations()) {
            rows.add(row(recommendation));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reply", answer.reply());
        body.put("recommendations", rows);
        body.put("taste", taste(answer.taste()));
        return Json.stringify(body);
    }

    public static String tasteResponse(UserPreferences preferences) {
        return Json.stringify(taste(preferences));
    }

    private static Map<String, Object> taste(UserPreferences preferences) {
        Map<String, Object> taste = new LinkedHashMap<>();
        taste.put("interests", preferences.getInterests());
        taste.put("likes", preferences.getLikes());
        taste.put("dislikes", preferences.getDislikes());
        taste.put("priceComfort", preferences.getPriceComfort());
        taste.put("description", preferences.getDescription());
        return taste;
    }

    private static Map<String, Object> row(Recommendation recommendation) {
        Restaurant restaurant = recommendation.getRestaurant();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("restId", restaurant.getRestId());
        row.put("name", restaurant.getName());
        row.put("cuisine", restaurant.getCuisine());
        row.put("address", restaurant.getAddress());
        row.put("latitude", restaurant.hasLocation() ? restaurant.getLocation().getLatitude() : null);
        row.put("longitude", restaurant.hasLocation() ? restaurant.getLocation().getLongitude() : null);
        row.put("rating", restaurant.getRating());
        row.put("priceRange", restaurant.getPriceRange());
        row.put("status", restaurant.getStatus());
        row.put("tags", restaurant.getTags());
        row.put("photo", recommendation.getPhoto());
        row.put("explanation", recommendation.getExplanation());
        row.put("matchReasons", recommendation.getMatchReasons());
        return row;
    }

    private static String photoPath(String photoName) {
        if (photoName == null || photoName.isBlank()) {
            return "";
        }
        return "/api/places/photo?name=" + URLEncoder.encode(photoName, StandardCharsets.UTF_8);
    }

    private static String join(String message, List<String> chips) {
        StringBuilder out = new StringBuilder(message == null ? "" : message.trim());
        if (chips != null) {
            for (String chip : chips) {
                if (chip == null || chip.isBlank()) {
                    continue;
                }
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(chip.trim());
            }
        }
        return out.toString();
    }

    private static String display(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String shorten(String reply) {
        String trimmed = reply.trim();
        if (trimmed.length() <= 1400) {
            return trimmed;
        }
        return trimmed.substring(0, 1400).trim() + "…";
    }

    public record Answer(String reply, List<Recommendation> recommendations, UserPreferences taste) {
    }

    public record Turn(String role, String text) {
    }
}
