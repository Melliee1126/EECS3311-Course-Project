package foodassistant.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import foodassistant.account.AccountBook;
import foodassistant.account.CollectionStore;
import foodassistant.account.SignInRequired;
import foodassistant.account.SupabaseDatabase;
import foodassistant.collection.CollectionLibrary;
import foodassistant.model.Account;
import foodassistant.model.Collection;
import foodassistant.model.Restaurant;
import foodassistant.model.Review;
import foodassistant.review.ReviewBoard;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The Java program the page talks to. Accounts and collections are handled here.
 */
public final class CollectionServer {
    private final AccountBook accounts;
    private final CollectionStore collections;
    private final ReviewBoard reviews;
    private final HttpServer server;

    public CollectionServer(AccountBook accounts, CollectionStore collections, ReviewBoard reviews, int port) throws IOException {
        this.accounts = accounts;
        this.collections = collections;
        this.reviews = reviews;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/api/accounts", this::accounts);
        this.server.createContext("/api/sessions", this::sessions);
        this.server.createContext("/api/collections", this::collections);
        this.server.createContext("/api/reviews", this::reviews);
        this.server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        SupabaseDatabase database = SupabaseDatabase.open(Path.of("supabase.local"));
        AccountBook accounts = AccountBook.load(database);
        CollectionStore collections = new CollectionStore(database);
        ReviewBoard reviews = new ReviewBoard(database);
        CollectionServer server = new CollectionServer(accounts, collections, reviews, port);
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
                        text(body, "password"));
                send(exchange, 200, AccountBook.signedInResponse(signedIn));
                return;
            }
            if ("me".equals(tail) && "GET".equals(method)) {
                Account account = requireAccount(exchange);
                send(exchange, 200, AccountBook.accountResponse(account));
                return;
            }
            if ("me".equals(tail) && "PATCH".equals(method)) {
                Account account = requireAccount(exchange);
                Map<String, Object> body = object(body(exchange));
                Account updated = accounts.updateProfile(
                        account.getId(),
                        text(body, "name"),
                        text(body, "email"),
                        text(body, "birthday"));
                send(exchange, 200, AccountBook.accountResponse(updated));
                return;
            }
            if ("me/password".equals(tail) && "POST".equals(method)) {
                Account account = requireAccount(exchange);
                Map<String, Object> body = object(body(exchange));
                accounts.changePassword(account.getId(), text(body, "currentPassword"), text(body, "password"));
                send(exchange, 200, AccountBook.accountResponse(accounts.accountFor(token(exchange))));
                return;
            }
            send(exchange, 404, error("That page was not found."));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error("Could not update the account."));
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
                AccountBook.SignedIn signedIn = accounts.signIn(text(body, "email"), text(body, "password"));
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

    private void reviews(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) {
            return;
        }
        try {
            Account account = requireAccount(exchange);
            String method = exchange.getRequestMethod();
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
            routeCollections(exchange, collections.forAccount(account.getId()));
        } catch (SignInRequired exception) {
            send(exchange, 401, error(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, error(exception.getMessage()));
        } catch (RuntimeException exception) {
            send(exchange, 500, error("Could not update collections."));
        }
    }

    private void routeCollections(HttpExchange exchange, CollectionLibrary library) throws IOException {
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
            sendCollections(exchange, library, null);
            return;
        }
        if (parts.length >= 2 && "places".equals(parts[1])) {
            if (parts.length == 2 && "POST".equals(method)) {
                library.addPlace(collectionId, CollectionLibrary.restaurant(Json.parse(body(exchange))));
                sendCollections(exchange, library, null);
                return;
            }
            if (parts.length == 3 && "DELETE".equals(method)) {
                library.removePlace(collectionId, decode(parts[2]));
                sendCollections(exchange, library, null);
                return;
            }
        }
        send(exchange, 404, error("Collection was not found."));
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

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
