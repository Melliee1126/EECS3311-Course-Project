package foodassistant.cli;

import foodassistant.collection.CollectionLibrary;
import foodassistant.map.ExternalMapClient;
import foodassistant.map.MapProviderAdapter;
import foodassistant.map.MapService;
import foodassistant.model.Restaurant;
import foodassistant.server.Json;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FoodAssistantCli {
    private static final double TORONTO_LAT = 43.70;
    private static final double TORONTO_LNG = -79.38;
    private static final String SERVER = "http://localhost:8080";

    private FoodAssistantCli() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            run(args);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println(exception.getMessage());
        }
    }

    private static void run(String[] args) throws IOException, InterruptedException {
        String query = "";
        String cuisine = "";
        boolean mapAvailable = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--query" -> query = next(args, ++i);
                case "--cuisine" -> cuisine = next(args, ++i);
                case "--unavailable" -> mapAvailable = false;
                case "--help" -> {
                    printHelp();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        String text = query.isBlank() ? cuisine : cuisine.isBlank() ? query : query + " " + cuisine;
        if (text.isBlank()) {
            printHelp();
            throw new IllegalArgumentException("Type a restaurant search.");
        }
        List<Restaurant> restaurants = search(text);
        ExternalMapClient client = new ExternalMapClient(mapAvailable, System.out);
        MapService mapService = new MapService(new MapProviderAdapter(client), System.out);
        mapService.show(restaurants);
    }

    @SuppressWarnings("unchecked")
    private static List<Restaurant> search(String text) throws IOException, InterruptedException {
        String url = SERVER + "/api/search?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&lat=" + TORONTO_LAT + "&lng=" + TORONTO_LNG;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException exception) {
            throw new IllegalStateException("The Java server is not running. Start CollectionServer on port 8080.");
        }
        Object parsed = Json.parse(response.body());
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("The Java server returned an unexpected search result.");
        }
        Map<String, Object> body = (Map<String, Object>) raw;
        if (response.statusCode() != 200) {
            Object message = body.get("error");
            throw new IllegalStateException(message instanceof String detail && !detail.isBlank()
                    ? detail
                    : "Couldn't load restaurants from Google Places.");
        }
        if (!(body.get("restaurants") instanceof List<?> list)) {
            return List.of();
        }
        List<Restaurant> restaurants = new ArrayList<>();
        for (Object item : list) {
            restaurants.add(CollectionLibrary.restaurant(item));
        }
        return restaurants;
    }

    private static String next(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static void printHelp() {
        System.out.println("""
                Food search
                  java -cp out foodassistant.cli.FoodAssistantCli --query text [--cuisine text] [--unavailable]
                The Java server must already be running. --unavailable prints a list instead of map pins.
                """);
    }
}
