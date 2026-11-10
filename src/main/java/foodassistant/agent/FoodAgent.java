package foodassistant.agent;

import foodassistant.llm.LLMEngine;
import foodassistant.llm.LLMFactory;
import foodassistant.model.Constraint;
import foodassistant.model.FoodWords;
import foodassistant.model.Recommendation;
import foodassistant.model.RecommendationRequest;
import foodassistant.model.Restaurant;
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
    private final IntentExtractor intents;
    private final ResponseGenerator responses;
    private final CandidatesFilter candidatesFilter = new CandidatesFilter();
    private final RankingStrategy tasteRanking = new TasteBasedRanking();
    private final RankingStrategy distanceRanking = new DistanceBasedRanking();

    public FoodAgent(PlacesSearch places, MemoryService memory, LLMFactory llmFactory) {
        this.places = places;
        this.memory = memory;
        LLMEngine llm = llmFactory.createLLM();
        this.intents = new IntentExtractor(llm);
        this.responses = new ResponseGenerator(llm);
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
        Intent intent = intents.extract(asked, earlier);
        if (forYou) {
            intent = intent.asSearch();
        }
        MemoryService.Snapshot snapshot = memory.remember(accountId);
        RecommendationRequest request = requestFrom(asked, intent, latitude, longitude);
        if (!intent.search()) {
            String reply = responses.generate("ask", intent, request, snapshot, List.of(), earlier, false, false);
            return new Answer(reply, List.of(), snapshot.preferences());
        }
        return getRecommendations(request, intent, latitude, longitude, forYou, snapshot, earlier);
    }

    public Answer getRecommendations(
            RecommendationRequest request,
            Intent intent,
            double latitude,
            double longitude,
            boolean forYou,
            MemoryService.Snapshot snapshot,
            List<Turn> history) {
        UserPreferences taste = snapshot.preferences();
        UserPreferences rankingTaste = TasteProfileUpdater.overlay(taste, snapshot.saved());
        Map<String, String> photos = new LinkedHashMap<>();
        Retrieved retrieved = retrieveRestaurants(intent, rankingTaste, latitude, longitude, forYou, photos);
        List<Restaurant> filtered = candidatesFilter.filter(retrieved.restaurants(), request.getConstraints());
        if (filtered.isEmpty()) {
            String reply = responses.generate("none", intent, request, snapshot, List.of(), history, forYou, retrieved.budgetRelaxed());
            return new Answer(reply, List.of(), taste);
        }
        List<Recommendation> ranked = rankRestaurants(filtered, request, rankingTaste, latitude, longitude, forYou, photos);
        int keep = Math.min(5, ranked.size());
        List<Recommendation> top = List.copyOf(ranked.subList(0, keep));
        String reply = responses.generate("recommend", intent, request, snapshot, top, history, forYou, retrieved.budgetRelaxed());
        return new Answer(reply, top, taste);
    }

    private Retrieved retrieveRestaurants(
            Intent intent,
            UserPreferences preferences,
            double latitude,
            double longitude,
            boolean forYou,
            Map<String, String> photos) {
        String text = searchText(intent, preferences, forYou);
        List<String> levels = priceLevels(intent.budget());
        RuntimeException searchProblem = null;
        List<PlacesSearch.Hit> hits = List.of();
        boolean budgetRelaxed = false;
        try {
            if (levels.isEmpty()) {
                hits = places.search(text, latitude, longitude);
            } else {
                hits = places.search(text, latitude, longitude, levels);
                if (hits.size() < 3) {
                    budgetRelaxed = true;
                    hits = merge(hits, places.search(text, latitude, longitude));
                }
            }
        } catch (RuntimeException exception) {
            if (!levels.isEmpty()) {
                try {
                    hits = places.search(text, latitude, longitude);
                    budgetRelaxed = true;
                } catch (RuntimeException second) {
                    searchProblem = second;
                }
            } else {
                searchProblem = exception;
            }
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
        return new Retrieved(new ArrayList<>(byId.values()), budgetRelaxed);
    }

    private static List<PlacesSearch.Hit> merge(List<PlacesSearch.Hit> first, List<PlacesSearch.Hit> second) {
        Map<String, PlacesSearch.Hit> byId = new LinkedHashMap<>();
        for (PlacesSearch.Hit hit : first) {
            byId.put(hit.restaurant().getRestId(), hit);
        }
        for (PlacesSearch.Hit hit : second) {
            byId.putIfAbsent(hit.restaurant().getRestId(), hit);
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
        Intent intent = new Intent("", "", "", "", recommendations.isEmpty() ? "ask" : "search");
        return responses.generate(
                recommendations.isEmpty() ? "none" : "recommend",
                intent,
                request,
                snapshot,
                recommendations,
                List.of(),
                forYou,
                false);
    }

    private static RecommendationRequest requestFrom(String asked, Intent intent, double latitude, double longitude) {
        List<Constraint> constraints = new ArrayList<>();
        if (!intent.budget().isBlank()) {
            constraints.add(new Constraint(Constraint.BUDGET, intent.budget(), 1));
        }
        for (String word : FoodWords.mentioned(intent.cuisine())) {
            String type = dietary(word) ? Constraint.DIETARY : Constraint.CUISINE;
            constraints.add(new Constraint(type, word, dietary(word) ? 1 : 2));
        }
        boolean near = "near me".equalsIgnoreCase(intent.location())
                || intent.location().toLowerCase(java.util.Locale.ROOT).contains("nearby");
        if (near) {
            constraints.add(new Constraint(Constraint.DISTANCE, "near", 2));
        }
        double maxPrice = switch (intent.budget()) {
            case "$" -> 1;
            case "$$" -> 2;
            case "$$$" -> 3;
            case "$$$$" -> 4;
            default -> 0;
        };
        return new RecommendationRequest(asked, latitude + ", " + longitude, "", maxPrice, constraints, near);
    }

    private static String searchText(Intent intent, UserPreferences preferences, boolean forYou) {
        StringBuilder query = new StringBuilder();
        if (!forYou) {
            if (!intent.dish().isBlank()) {
                query.append(intent.dish());
            }
            if (!intent.cuisine().isBlank() && !intent.dish().toLowerCase(java.util.Locale.ROOT).contains(intent.cuisine().toLowerCase(java.util.Locale.ROOT))) {
                if (!query.isEmpty()) {
                    query.append(' ');
                }
                query.append(intent.cuisine());
            }
            if (!intent.location().isBlank() && !"near me".equalsIgnoreCase(intent.location())) {
                query.append(' ').append(intent.location());
            }
        }
        if (query.isEmpty() && !preferences.getInterests().isEmpty()) {
            int count = Math.min(3, preferences.getInterests().size());
            return String.join(" ", preferences.getInterests().subList(0, count)) + " restaurants";
        }
        if (query.isEmpty() && !preferences.getLikes().isEmpty()) {
            return preferences.getLikes().get(0) + " restaurants";
        }
        if (query.isEmpty()) {
            return "restaurants";
        }
        return query.toString().trim() + " restaurants";
    }

    private static List<String> priceLevels(String budget) {
        return switch (budget) {
            case "$" -> List.of("PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE");
            case "$$" -> List.of("PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE", "PRICE_LEVEL_MODERATE");
            case "$$$" -> List.of(
                    "PRICE_LEVEL_FREE",
                    "PRICE_LEVEL_INEXPENSIVE",
                    "PRICE_LEVEL_MODERATE",
                    "PRICE_LEVEL_EXPENSIVE");
            default -> List.of();
        };
    }

    private static boolean dietary(String word) {
        String normal = FoodWords.normalize(word);
        return normal.equals("vegan")
                || normal.equals("vegetarian")
                || normal.equals("halal")
                || normal.equals("gluten free");
    }

    private record Retrieved(List<Restaurant> restaurants, boolean budgetRelaxed) {
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

    public record Answer(String reply, List<Recommendation> recommendations, UserPreferences taste) {
    }

    public record Turn(String role, String text) {
    }
}
