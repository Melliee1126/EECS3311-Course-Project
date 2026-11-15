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
import foodassistant.taste.ChatPreference;
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
        String focus = forYou ? asked : RequestSense.focus(asked, earlier);
        ChatPreference.Reading reading = forYou ? ChatPreference.none() : ChatPreference.read(asked);
        MemoryService.Snapshot snapshot = memory.remember(accountId);
        if (!reading.stated().isEmpty() || !reading.cleared().isEmpty()) {
            UserPreferences learned = TasteProfileUpdater.apply(
                    snapshot.preferences(), reading.stated(), reading.cleared(), snapshot.reviews(), snapshot.saved());
            if (accountId != null && !accountId.isBlank()) {
                memory.updateMemory(accountId, learned);
            }
            snapshot = new MemoryService.Snapshot(learned, snapshot.saved(), snapshot.reviews());
        }
        intent = steer(intent, reading);
        RecommendationRequest request = requestFrom(focus, intent, latitude, longitude);
        String remembered = ChatPreference.note(reading);
        if (!intent.search()) {
            String reply = responses.generate("ask", intent, request, snapshot, List.of(), earlier, false, false, false, asked, remembered);
            return new Answer(reply, List.of(), snapshot.preferences());
        }
        return getRecommendations(request, intent, latitude, longitude, forYou, snapshot, earlier, asked, remembered, ChatPreference.rejected(reading));
    }

    public Answer getRecommendations(
            RecommendationRequest request,
            Intent intent,
            double latitude,
            double longitude,
            boolean forYou,
            MemoryService.Snapshot snapshot,
            List<Turn> history,
            String latest,
            String remembered,
            List<String> rejected) {
        UserPreferences taste = snapshot.preferences();
        UserPreferences rankingTaste = TasteProfileUpdater.overlay(taste, snapshot.saved());
        Map<String, String> photos = new LinkedHashMap<>();
        Retrieved retrieved = retrieveRestaurants(intent, rankingTaste, latitude, longitude, forYou, photos, rejected);
        List<Restaurant> filtered = candidatesFilter.filter(retrieved.restaurants(), request.getConstraints());
        filtered = withoutDisliked(filtered, taste, rejected);
        if (filtered.isEmpty()) {
            String reply = responses.generate("none", intent, request, snapshot, List.of(), history, forYou, retrieved.budgetRelaxed(), retrieved.broadened(), latest, remembered);
            return new Answer(reply, List.of(), taste);
        }
        List<Recommendation> ranked = rankRestaurants(filtered, request, rankingTaste, latitude, longitude, forYou, photos);
        if (!intent.style().isBlank()) {
            ranked = preferAtmosphere(ranked);
        }
        int keep = Math.min(5, ranked.size());
        List<Recommendation> top = List.copyOf(ranked.subList(0, keep));
        String reply = responses.generate("recommend", intent, request, snapshot, top, history, forYou, retrieved.budgetRelaxed(), retrieved.broadened(), latest, remembered);
        return new Answer(reply, top, taste);
    }

    private Retrieved retrieveRestaurants(
            Intent intent,
            UserPreferences preferences,
            double latitude,
            double longitude,
            boolean forYou,
            Map<String, String> photos,
            List<String> rejected) {
        String text = withoutWords(withDiet(searchText(intent, preferences, forYou), preferences), rejected);
        text = withoutWords(text, preferences.avoids());
        String placeType = RequestSense.placeType(intent);
        List<String> levels = priceLevels(intent.budget());
        RuntimeException searchProblem = null;
        List<PlacesSearch.Hit> hits = List.of();
        boolean budgetRelaxed = false;
        boolean broadened = false;
        try {
            hits = searchPlaces(text, latitude, longitude, levels, placeType);
            if (!levels.isEmpty() && hits.size() < 3) {
                budgetRelaxed = true;
                hits = merge(hits, searchPlaces(text, latitude, longitude, List.of(), placeType));
            }
            if (hits.isEmpty() && !"restaurant".equals(placeType)) {
                List<PlacesSearch.Hit> widerType = places.search(text, latitude, longitude);
                if (!widerType.isEmpty()) {
                    hits = widerType;
                    broadened = true;
                }
            }
            if (hits.isEmpty()) {
                String wider = widerQuery(intent, preferences, forYou, text);
                if (!wider.equalsIgnoreCase(text)) {
                    List<PlacesSearch.Hit> widerHits = places.search(wider, latitude, longitude);
                    if (!widerHits.isEmpty()) {
                        hits = widerHits;
                        broadened = true;
                    }
                }
            }
        } catch (RuntimeException exception) {
            try {
                hits = places.search(text, latitude, longitude);
                budgetRelaxed = !levels.isEmpty();
                broadened = !"restaurant".equals(placeType) && !hits.isEmpty();
                if (hits.isEmpty()) {
                    String wider = widerQuery(intent, preferences, forYou, text);
                    if (!wider.equalsIgnoreCase(text)) {
                        hits = places.search(wider, latitude, longitude);
                        broadened = !hits.isEmpty();
                    }
                }
            } catch (RuntimeException second) {
                searchProblem = second;
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
        return new Retrieved(new ArrayList<>(byId.values()), budgetRelaxed, broadened);
    }

    private List<PlacesSearch.Hit> searchPlaces(
            String text,
            double latitude,
            double longitude,
            List<String> levels,
            String placeType) {
        if (levels.isEmpty()) {
            return places.search(text, latitude, longitude, List.of(), placeType);
        }
        return places.search(text, latitude, longitude, levels, placeType);
    }

    private static String widerQuery(Intent intent, UserPreferences preferences, boolean forYou, String first) {
        if (forYou || (intent.dish().isBlank() && intent.cuisine().isBlank())) {
            return first;
        }
        if (intent.location().isBlank() || "near me".equalsIgnoreCase(intent.location())) {
            return first;
        }
        Intent kept = new Intent(intent.dish(), intent.cuisine(), intent.budget(), "", intent.intent(), intent.style());
        String wider = searchText(kept, preferences, false);
        if (wider.equalsIgnoreCase(first) || "restaurants".equalsIgnoreCase(wider)) {
            return first;
        }
        return wider;
    }

    private static List<Recommendation> preferAtmosphere(List<Recommendation> ranked) {
        List<Recommendation> adjusted = new ArrayList<>();
        for (Recommendation recommendation : ranked) {
            Restaurant restaurant = recommendation.getRestaurant();
            double bonus = 0;
            int level = restaurant.getPriceRange() == null ? 0 : restaurant.getPriceRange().length();
            if (level >= 3) {
                bonus += 1.2;
            } else if (level == 2) {
                bonus += 0.4;
            }
            if (restaurant.getRating() >= 4.5) {
                bonus += 0.8;
            }
            adjusted.add(new Recommendation(
                    restaurant,
                    recommendation.getPhoto(),
                    recommendation.getScore() + bonus,
                    recommendation.getExplanation(),
                    recommendation.getMatchReasons()));
        }
        adjusted.sort((left, right) -> {
            int byScore = Double.compare(right.getScore(), left.getScore());
            if (byScore != 0) {
                return byScore;
            }
            return left.getRestaurant().getName().compareToIgnoreCase(right.getRestaurant().getName());
        });
        return adjusted;
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
                false,
                false,
                "",
                "");
    }

    private static Intent steer(Intent intent, ChatPreference.Reading reading) {
        List<String> rejected = ChatPreference.rejected(reading);
        if (rejected.isEmpty()) {
            return intent;
        }
        String cuisine = keepFoods(intent.cuisine(), rejected);
        String dish = stripFoods(intent.dish(), rejected);
        dish = stripPreferenceWords(dish);
        if (cuisine.isBlank() && dish.isBlank() && intent.budget().isBlank() && intent.location().isBlank()) {
            return new Intent("", "", "", "", "search", intent.style());
        }
        return new Intent(dish, cuisine, intent.budget(), intent.location(), "search", intent.style());
    }

    private static String keepFoods(String text, List<String> rejected) {
        List<String> kept = new ArrayList<>();
        for (String word : FoodWords.mentioned(text)) {
            if (!listed(rejected, word)) {
                kept.add(word);
            }
        }
        return String.join(" ", kept);
    }

    private static String stripFoods(String text, List<String> rejected) {
        String remaining = " " + FoodWords.normalize(text) + " ";
        for (String word : rejected) {
            remaining = remaining.replace(" " + FoodWords.normalize(word) + " ", " ");
        }
        return remaining.replaceAll("\\s+", " ").trim();
    }

    private static String stripPreferenceWords(String dish) {
        if (dish.isBlank() || !FoodWords.mentioned(dish).isEmpty()) {
            return dish;
        }
        String cleaned = dish.replaceAll(
                "\\b(absolutely|really|truly|love|loved|like|liked|hate|hated|adore|enjoy|enjoyed|prefer|preferred|dislike|disliked|favorite|favourite|obsessed|allergic|avoid|stand|want|wanted|mildly|kinda|fan|into|never|always|usually|dont|not|no)\\b",
                " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.length() < 3 ? "" : cleaned;
    }

    private static List<Restaurant> withoutDisliked(
            List<Restaurant> restaurants,
            UserPreferences preferences,
            List<String> rejected) {
        List<String> blocked = new ArrayList<>(preferences.getDislikes());
        for (String avoid : preferences.avoids()) {
            if (!listed(blocked, avoid)) {
                blocked.add(avoid);
            }
        }
        if (rejected != null) {
            for (String word : rejected) {
                if (!listed(blocked, word)) {
                    blocked.add(word);
                }
            }
        }
        if (restaurants.isEmpty() || blocked.isEmpty()) {
            return restaurants;
        }
        List<Restaurant> kept = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            boolean skip = false;
            for (String dislike : blocked) {
                if (RestaurantMatch.matches(restaurant, dislike)) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                kept.add(restaurant);
            }
        }
        return kept;
    }

    private static String withoutWords(String text, List<String> rejected) {
        if (rejected == null || rejected.isEmpty() || text == null || text.isBlank()) {
            return text;
        }
        String remaining = " " + FoodWords.normalize(text) + " ";
        for (String word : rejected) {
            remaining = remaining.replace(" " + FoodWords.normalize(word) + " ", " ");
        }
        remaining = remaining.replaceAll("\\s+", " ").trim();
        if (remaining.isBlank() || remaining.equals("restaurants")) {
            return "restaurants";
        }
        return remaining.endsWith("restaurants") ? remaining : remaining + " restaurants";
    }

    private static String withDiet(String query, UserPreferences preferences) {
        List<String> diets = preferences.diets();
        if (diets.isEmpty() || query == null || query.isBlank()) {
            return query;
        }
        String lower = " " + FoodWords.normalize(query) + " ";
        List<String> extra = new ArrayList<>();
        for (String diet : diets) {
            String normal = FoodWords.normalize(diet);
            if (!lower.contains(" " + normal + " ")) {
                extra.add(normal);
            }
        }
        if (extra.isEmpty()) {
            return query;
        }
        String addition = String.join(" ", extra);
        String base = query.trim();
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(" restaurants")) {
            base = base.substring(0, base.length() - " restaurants".length()).trim();
        }
        if (base.isBlank() || base.equalsIgnoreCase("restaurants")) {
            return addition + " restaurants";
        }
        return base + " " + addition + " restaurants";
    }

    private static String preferredFoods(UserPreferences preferences) {
        List<String> foods = new ArrayList<>();
        for (String interest : preferences.getInterests()) {
            if (!listed(preferences.getDislikes(), interest)) {
                foods.add(interest);
            }
            if (foods.size() == 3) {
                break;
            }
        }
        if (foods.isEmpty()) {
            for (String like : preferences.getLikes()) {
                if (!listed(preferences.getDislikes(), like)) {
                    foods.add(like);
                    break;
                }
            }
        }
        return String.join(" ", foods);
    }

    private static boolean listed(List<String> values, String word) {
        for (String value : values) {
            if (value.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    private static RecommendationRequest requestFrom(String asked, Intent intent, double latitude, double longitude) {
        List<Constraint> constraints = new ArrayList<>();
        if (!intent.budget().isBlank()) {
            constraints.add(new Constraint(Constraint.BUDGET, intent.budget(), 1));
        }
        for (String word : FoodWords.mentioned(intent.cuisine())) {
            String type = FoodWords.isDiet(word) ? Constraint.DIETARY : Constraint.CUISINE;
            constraints.add(new Constraint(type, word, FoodWords.isDiet(word) ? 1 : 2));
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
        if (query.isEmpty()) {
            String tastes = preferredFoods(preferences);
            if (!tastes.isBlank()) {
                return tastes + " restaurants";
            }
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

    private record Retrieved(List<Restaurant> restaurants, boolean budgetRelaxed, boolean broadened) {
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
        taste.put("diet", preferences.diets());
        taste.put("avoids", preferences.avoids());
        taste.put("priceComfort", preferences.getPriceComfort());
        taste.put("description", preferences.getDescription());
        taste.put("affinity", preferences.getAffinity());
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
        row.put("priceSpan", restaurant.getPriceSpan());
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
