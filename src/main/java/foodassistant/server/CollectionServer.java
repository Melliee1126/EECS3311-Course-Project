package foodassistant.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import foodassistant.account.AccountBook;
import foodassistant.account.CollectionStore;
import foodassistant.account.SignInRequired;
import foodassistant.account.SupabaseDatabase;
import foodassistant.agent.FoodAgent;
import foodassistant.collection.CollectionLibrary;
import foodassistant.feedback.FeedbackPublisher;
import foodassistant.llm.LLMFactory;
import foodassistant.model.Account;
import foodassistant.model.Collection;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.model.UserPreferences;
import foodassistant.places.PlacesSearch;
import foodassistant.review.ReviewBoard;
import foodassistant.taste.MemoryService;
import foodassistant.taste.TasteProfileUpdater;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The Java program the page talks to. Accounts and collections are handled here.
 */
public final class CollectionServer {
    private final AccountBook accounts;
    private final CollectionStore collections;
    private final ReviewBoard reviews;
    private final PlacesSearch places;
    private final MemoryService memory;
    private final FoodAgent agent;
    private final FeedbackPublisher feedback;
    private final HttpServer server;

    public CollectionServer(
            AccountBook accounts,
            CollectionStore collections,
            ReviewBoard reviews,
            PlacesSearch places,
            MemoryService memory,
            FoodAgent agent,
            FeedbackPublisher feedback,
            int port) throws IOException {
        this.accounts = accounts;
        this.collections = collections;
        this.reviews = reviews;
        this.places = places;
        this.memory = memory;
        this.agent = agent;
        this.feedback = feedback;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/api/accounts", this::accounts);
        this.server.createContext("/api/sessions", this::sessions);
        this.server.createContext("/api/friends", this::friends);
        this.server.createContext("/api/collections", this::collections);
        this.server.createContext("/api/reviews", this::reviews);
        this.server.createContext("/api/search", this::search);
        this.server.createContext("/api/places/photo", this::photo);
        this.server.createContext("/api/chat", this::chat);
        this.server.createContext("/api/taste", this::taste);
        this.server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        Path settings = Path.of("supabase.local");
        SupabaseDatabase database = SupabaseDatabase.open(settings);
        AccountBook accounts = AccountBook.load(database);
        CollectionStore collections = new CollectionStore(database);
        MemoryService memory = new MemoryService(database);
        FeedbackPublisher feedback = new FeedbackPublisher();
        feedback.subscribe(new TasteProfileUpdater(memory));
        ReviewBoard reviews = new ReviewBoard(database, feedback);
        PlacesSearch places = PlacesSearch.fromFile(settings);
        FoodAgent agent = new FoodAgent(places, memory, LLMFactory.fromFile(settings));
        CollectionServer server = new CollectionServer(accounts, collections, reviews, places, memory, agent, feedback, port);
        server.start();
        System.out.println("Java program listening on http://localhost:" + port);
    }

    public void start() {
        server.start();
    }

    private void accounts(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            String method = exchange.getRequestMethod();
            String tail = tail(exchange, "/api/accounts");
            if (tail.isEmpty() && "POST".equals(method)) {
                Map<String, Object> body = object(body(exchange));
                AccountBook.SignedIn signedIn = accounts.register(
                        text(body, "name"),
                        text(body, "email"),
                        text(body, "password"),
                        redirectTo(exchange));
                send(exchange, 200, AccountBook.signedInResponse(signedIn));
                return;
            }
            if ("me".equals(tail) && "GET".equals(method)) {
                Account account = requireAccount(exchange);
                send(exchange, 200, AccountBook.accountResponse(account));
                return;
            }
            if ("me".equals(tail) && "PATCH".equals(method)) {
                requireAccount(exchange);
                Map<String, Object> body = object(body(exchange));
                Account updated = accounts.updateProfile(
                        token(exchange),
                        text(body, "name"),
                        text(body, "email"),
                        text(body, "birthday"));
                send(exchange, 200, AccountBook.accountResponse(updated));
                return;
            }
            if ("lookup".equals(tail) && "POST".equals(method)) {
                Account account = requireAccount(exchange);
                Map<String, Object> body = object(body(exchange));
                AccountBook.FriendCard found = accounts.lookupFriend(account.getId(), text(body, "email"));
                send(exchange, 200, AccountBook.lookupResponse(found));
                return;
            }
            if ("recover".equals(tail) && "POST".equals(method)) {
                Map<String, Object> body = object(body(exchange));
                accounts.recover(text(body, "email"), redirectTo(exchange));
                send(exchange, 200, Json.stringify(Map.of(
                        "message", "If an account uses that email, a reset link is on its way.")));
                return;
            }
            if ("me/password".equals(tail) && "POST".equals(method)) {
                Map<String, Object> body = object(body(exchange));
                String current = body.get("currentPassword") == null ? null : text(body, "currentPassword");
                Account updated = accounts.changePassword(token(exchange), current, text(body, "password"));
                send(exchange, 200, AccountBook.accountResponse(updated));
                return;
            }
            if ("me/avatar".equals(tail) && "POST".equals(method)) {
                Account account = requireAccount(exchange);
                Map<String, Object> body = object(body(exchange));
                accounts.saveAvatar(account.getId(), text(body, "avatar"));
                send(exchange, 200, Json.stringify(Map.of("ok", true)));
                return;
            }
            send(exchange, 404, error("That page was not found."));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error(storageMessage(exception, "Could not update the account.")));
        }
    }

    private void friends(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            Account account = requireAccount(exchange);
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && "/api/friends".equals(path)) {
                send(exchange, 200, AccountBook.circleResponse(accounts.circle(account.getId())));
                return;
            }
            if ("POST".equals(method) && path.endsWith("/cancel")) {
                Map<String, Object> body = object(body(exchange));
                accounts.cancelRequest(account.getId(), text(body, "id"));
                send(exchange, 200, AccountBook.circleResponse(accounts.circle(account.getId())));
                return;
            }
            if ("POST".equals(method) && path.endsWith("/respond")) {
                Map<String, Object> body = object(body(exchange));
                Object flag = body.get("accept");
                if (!(flag instanceof Boolean accept)) {
                    throw new IllegalArgumentException("Choose accept or decline.");
                }
                accounts.respond(account.getId(), text(body, "id"), accept);
                send(exchange, 200, AccountBook.circleResponse(accounts.circle(account.getId())));
                return;
            }
            if ("POST".equals(method) && "/api/friends".equals(path)) {
                Map<String, Object> body = object(body(exchange));
                AccountBook.FriendCard card = accounts.requestFriend(account.getId(), text(body, "id"));
                send(exchange, 200, AccountBook.requestResponse(card, accounts.circle(account.getId())));
                return;
            }
            send(exchange, 405, error("Method not allowed"));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error(storageMessage(exception, "Could not update friends.")));
        }
    }

    private void sessions(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            String method = exchange.getRequestMethod();
            if ("POST".equals(method)) {
                Map<String, Object> body = object(body(exchange));
                String path = exchange.getRequestURI().getPath();
                AccountBook.SignedIn signedIn = path.endsWith("/refresh")
                        ? accounts.refresh(text(body, "refreshToken"))
                        : accounts.signIn(text(body, "email"), text(body, "password"));
                send(exchange, 200, AccountBook.signedInResponse(signedIn));
                return;
            }
            if ("DELETE".equals(method)) {
                accounts.signOut(token(exchange));
                send(exchange, 204, "");
                return;
            }
            send(exchange, 405, error("Method not allowed"));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error("Could not sign in."));
        }
    }

    private void search(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("Method not allowed"));
                return;
            }
            Map<String, String> query = query(exchange);
            String text = query.getOrDefault("q", "").trim();
            String cuisine = query.getOrDefault("cuisine", "").trim();
            String combined = text.isEmpty() ? cuisine : cuisine.isEmpty() ? text : text + " " + cuisine;
            double latitude = coordinate(query.get("lat"), 43.70);
            double longitude = coordinate(query.get("lng"), -79.38);
            send(exchange, 200, PlacesSearch.response(places.search(combined, latitude, longitude)));
        } catch (IllegalStateException exception) {
            send(exchange, 500, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error("Couldn't load restaurants from Google Places."));
        }
    }

    private void photo(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("Method not allowed"));
                return;
            }
            PlacesSearch.Photo photo = places.photo(query(exchange).getOrDefault("name", ""));
            byte[] bytes = photo.bytes();
            exchange.getResponseHeaders().set("Content-Type", photo.contentType());
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IllegalArgumentException exception) {
            send(exchange, 404, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 404, error("That photo was not found."));
        }
    }

    private void chat(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("Method not allowed"));
                return;
            }
            Account account = requireAccount(exchange);
            Map<String, Object> body = object(body(exchange));
            FoodAgent.Answer answer = agent.processRequest(
                    account.getId(),
                    text(body, "message"),
                    strings(body.get("chips")),
                    history(body.get("history")),
                    numberOr(body.get("latitude"), 43.70),
                    numberOr(body.get("longitude"), -79.38),
                    Boolean.TRUE.equals(body.get("forYou")));
            send(exchange, 200, FoodAgent.response(answer));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (IllegalStateException exception) {
            send(exchange, 500, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error("Could not get a recommendation."));
        }
    }

    private void taste(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            Account account = requireAccount(exchange);
            String method = exchange.getRequestMethod();
            if ("GET".equals(method)) {
                send(exchange, 200, FoodAgent.tasteResponse(memory.retrieveRelevantMemory(account.getId())));
                return;
            }
            if ("PUT".equals(method)) {
                Map<String, Object> body = object(body(exchange));
                UserPreferences saved = memory.saveExplicit(
                        account.getId(),
                        strings(body.get("interests")),
                        text(body, "description"));
                send(exchange, 200, FoodAgent.tasteResponse(saved));
                return;
            }
            send(exchange, 405, error("Method not allowed"));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error("Could not update the taste profile."));
        }
    }

    private void reviews(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            Account account = requireAccount(exchange);
            String method = exchange.getRequestMethod();
            String tail = tail(exchange, "/api/reviews");
            if (tail.isEmpty()) {
                if ("GET".equals(method)) {
                    send(exchange, 200, ReviewBoard.listResponse(reviews.latest(account.getId())));
                    return;
                }
                if ("POST".equals(method)) {
                    Review created = reviews.publish(account.getId(), object(body(exchange)));
                    send(exchange, 200, ReviewBoard.publishedResponse(created, reviews.latest(account.getId())));
                    return;
                }
                send(exchange, 405, error("Method not allowed"));
                return;
            }
            String id = decode(tail);
            if ("PATCH".equals(method)) {
                Review updated = reviews.update(account.getId(), id, object(body(exchange)));
                send(exchange, 200, ReviewBoard.publishedResponse(updated, reviews.latest(account.getId())));
                return;
            }
            if ("DELETE".equals(method)) {
                reviews.delete(account.getId(), id);
                send(exchange, 200, ReviewBoard.listResponse(reviews.latest(account.getId())));
                return;
            }
            send(exchange, 405, error("Method not allowed"));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.contains("reviews")) {
                send(exchange, 500, error("The reviews table is not in Supabase yet."));
                return;
            }
            send(exchange, 500, error("Could not save the review."));
        }
    }

    private void collections(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            Account account = requireAccount(exchange);
            routeCollections(exchange, collections.forAccount(account.getId()), account.getId());
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error("Could not update collections."));
        }
    }

    private void routeCollections(HttpExchange exchange, CollectionLibrary library, String accountId) throws IOException {
        String method = exchange.getRequestMethod();
        String tail = tail(exchange, "/api/collections");
        if (tail.isEmpty()) {
            if ("GET".equals(method)) {
                sendCollections(exchange, library, null);
                return;
            }
            if ("POST".equals(method)) {
                Map<String, Object> body = object(body(exchange));
                Object rawName = body.get("name");
                if (!(rawName instanceof String name)) {
                    throw new IllegalArgumentException("Name the collection first.");
                }
                Restaurant place = body.get("place") == null ? null : CollectionLibrary.restaurant(body.get("place"));
                Collection created = library.create(name, place);
                feedback.notifyObservers(accountId, null);
                sendCollections(exchange, library, created.getId());
                return;
            }
            send(exchange, 405, error("Method not allowed"));
            return;
        }

        String[] parts = tail.split("/", 3);
        String collectionId = decode(parts[0]);
        if (parts.length == 1 && "DELETE".equals(method)) {
            library.delete(collectionId);
            feedback.notifyObservers(accountId, null);
            sendCollections(exchange, library, null);
            return;
        }
        if (parts.length >= 2 && "places".equals(parts[1])) {
            if (parts.length == 2 && "POST".equals(method)) {
                library.addPlace(collectionId, CollectionLibrary.restaurant(Json.parse(body(exchange))));
                feedback.notifyObservers(accountId, null);
                sendCollections(exchange, library, null);
                return;
            }
            if (parts.length == 3 && "DELETE".equals(method)) {
                library.removePlace(collectionId, decode(parts[2]));
                feedback.notifyObservers(accountId, null);
                sendCollections(exchange, library, null);
                return;
            }
        }
        send(exchange, 404, error("Collection was not found."));
    }

    private static String redirectTo(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) {
            return "http://localhost:5173";
        }
        return origin;
    }

    private Account requireAccount(HttpExchange exchange) {
        Account account = accounts.accountFor(token(exchange));
        if (account == null) {
            throw new SignInRequired(AccountBook.SIGN_IN_FIRST);
        }
        return account;
    }

    private static String token(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) {
            return "";
        }
        String prefix = "Bearer ";
        if (header.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return header.substring(prefix.length()).trim();
        }
        return "";
    }

    private void sendCollections(HttpExchange exchange, CollectionLibrary library, String id) throws IOException {
        send(exchange, 200, CollectionLibrary.collectionsResponse(library.all(), id));
    }

    private static boolean preflight(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equals(exchange.getRequestMethod())) {
            return false;
        }
        send(exchange, 204, "");
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) map;
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Expected text for " + key);
    }

    private static List<FoodAgent.Turn> history(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<FoodAgent.Turn> turns = new java.util.ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Object role = raw.get("role");
            Object text = raw.get("text");
            if (!(role instanceof String roleText) || !(text instanceof String body)) {
                continue;
            }
            if (!"user".equals(roleText) && !"assistant".equals(roleText)) {
                continue;
            }
            String clipped = body.trim();
            if (clipped.isEmpty()) {
                continue;
            }
            if (clipped.length() > 800) {
                clipped = clipped.substring(0, 800);
            }
            turns.add(new FoodAgent.Turn(roleText, clipped));
            if (turns.size() == 8) {
                break;
            }
        }
        return turns;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) {
                values.add(text.trim());
            }
        }
        return values;
    }

    private static double numberOr(Object value, double fallback) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        return fallback;
    }

    private static String tail(HttpExchange exchange, String prefix) {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(prefix)) {
            return "";
        }
        String rest = path.substring(prefix.length());
        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }

    private static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        Map<String, String> query = new java.util.LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return query;
        }
        for (String part : raw.split("&")) {
            int split = part.indexOf('=');
            String key = decode(split < 0 ? part : part.substring(0, split));
            String value = decode(split < 0 ? "" : part.substring(split + 1));
            query.put(key, value);
        }
        return query;
    }

    private static double coordinate(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double coordinate = Double.parseDouble(value);
            if (Double.isFinite(coordinate)) {
                return coordinate;
            }
        } catch (NumberFormatException ignored) {
            return fallback;
        }
        return fallback;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String storageMessage(RuntimeException exception, String fallback) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("friendships") || message.contains("avatars")) {
            return "Run supabase/schema.sql again so friends and profile photos can be saved.";
        }
        return fallback;
    }

    private static String error(String message) {
        return Json.stringify(Map.of("error", message));
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
