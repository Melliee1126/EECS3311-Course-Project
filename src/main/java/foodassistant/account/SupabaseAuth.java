package foodassistant.account;

import foodassistant.server.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Talks to Supabase Auth for sign-up, sign-in, sessions, and password reset. */
public final class SupabaseAuth {
    private final String origin;
    private final String secret;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    SupabaseAuth(String origin, String secret) {
        this.origin = origin;
        this.secret = secret;
    }

    public SignUp signUp(String email, String password, String name, String redirectTo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("data", Map.of("name", name));
        Result result = call("POST", "/auth/v1/signup" + redirectQuery(redirectTo), Json.stringify(body), null);
        if (result.status >= 400 && redirectTo != null && !redirectTo.isBlank() && mentionsRedirect(result.body)) {
            return signUp(email, password, name, null);
        }
        if (result.status >= 400) {
            throw new IllegalArgumentException(message(result.body, "Could not create the account."));
        }
        return parseSignUp(result.body);
    }

    public AuthSession signIn(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        Result result = call("POST", "/auth/v1/token?grant_type=password", Json.stringify(body), null);
        if (result.status == 400 || result.status == 401) {
            throw new SignInRequired(message(result.body, "Email or password is wrong."));
        }
        if (result.status >= 400) {
            throw new IllegalStateException(message(result.body, "Could not sign in."));
        }
        return parseSession(result.body);
    }

    public AuthSession refresh(String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refresh_token", refreshToken);
        Result result = call("POST", "/auth/v1/token?grant_type=refresh_token", Json.stringify(body), null);
        if (result.status == 400 || result.status == 401) {
            throw new SignInRequired("Sign in again.");
        }
        if (result.status >= 400) {
            throw new IllegalStateException(message(result.body, "Could not refresh the session."));
        }
        return parseSession(result.body);
    }

    public AuthUser user(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        Result result = call("GET", "/auth/v1/user", null, accessToken);
        if (result.status == 401 || result.status == 403) {
            return null;
        }
        if (result.status >= 400) {
            throw new IllegalStateException("Could not reach Supabase.");
        }
        AuthUser parsed = userFrom(object(result.body));
        if (parsed != null) {
            return parsed;
        }
        return userFrom(object(result.body).get("user"));
    }

    public void signOut(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        call("POST", "/auth/v1/logout", null, accessToken);
    }

    public void recover(String email, String redirectTo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        if (redirectTo != null && !redirectTo.isBlank()) {
            body.put("redirect_to", redirectTo);
        }
        Result result = call("POST", "/auth/v1/recover", Json.stringify(body), null);
        if (result.status >= 400 && redirectTo != null && !redirectTo.isBlank() && mentionsRedirect(result.body)) {
            recover(email, null);
            return;
        }
        if (result.status >= 400) {
            throw new IllegalArgumentException(message(result.body, "Could not send the reset email."));
        }
    }

    public void updatePassword(String accessToken, String password) {
        Map<String, Object> body = Map.of("password", password);
        Result result = call("PUT", "/auth/v1/user", Json.stringify(body), accessToken);
        if (result.status >= 400) {
            throw new IllegalArgumentException(message(result.body, "Could not change the password."));
        }
    }

    public boolean updateEmail(String accessToken, String email) {
        Map<String, Object> body = Map.of("email", email);
        Result result = call("PUT", "/auth/v1/user", Json.stringify(body), accessToken);
        if (result.status >= 400) {
            throw new IllegalArgumentException(message(result.body, "Could not change the email."));
        }
        AuthUser updated = userFrom(object(result.body));
        if (updated == null) {
            updated = userFrom(object(result.body).get("user"));
        }
        return updated != null && email.equalsIgnoreCase(updated.email());
    }

    private AuthSession parseSession(String json) {
        Map<String, Object> map = object(json);
        AuthUser user = userFrom(map.get("user"));
        String access = text(map, "access_token");
        if (user == null || access.isBlank()) {
            throw new IllegalStateException("Supabase did not return a session.");
        }
        return new AuthSession(access, text(map, "refresh_token"), expires(map), user);
    }

    private SignUp parseSignUp(String json) {
        Map<String, Object> map = object(json);
        AuthUser user = userFrom(map.get("user"));
        if (user == null) {
            user = userFrom(map);
        }
        if (user == null) {
            throw new IllegalArgumentException("Could not create the account.");
        }
        String access = text(map, "access_token");
        if (access.isBlank()) {
            return new SignUp(null, user);
        }
        return new SignUp(new AuthSession(access, text(map, "refresh_token"), expires(map), user), user);
    }

    private Result call(String method, String path, String body, String bearer) {
        String token = bearer == null || bearer.isBlank() ? secret : bearer;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", secret)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("User-Agent", "Java-http-client/23");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), response.body() == null ? "" : response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Supabase did not answer.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not reach Supabase.", exception);
        }
    }

    private static String redirectQuery(String redirectTo) {
        if (redirectTo == null || redirectTo.isBlank()) {
            return "";
        }
        return "?redirect_to=" + URLEncoder.encode(redirectTo, StandardCharsets.UTF_8);
    }

    private static boolean mentionsRedirect(String body) {
        return body != null && body.toLowerCase().contains("redirect");
    }

    private static int expires(Map<String, Object> map) {
        Object value = map.get("expires_in");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return 3600;
    }

    @SuppressWarnings("unchecked")
    private static AuthUser userFrom(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        String id = text(map, "id");
        if (id.isBlank()) {
            return null;
        }
        String name = "";
        Object metadata = map.get("user_metadata");
        if (metadata instanceof Map<?, ?> meta && meta.get("name") instanceof String metaName) {
            name = metaName;
        }
        return new AuthUser(id, text(map, "email"), name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Unexpected response from Supabase Auth.");
        }
        return (Map<String, Object>) map;
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String text ? text : "";
    }

    private static String message(String body, String fallback) {
        try {
            Map<String, Object> map = object(body);
            String raw = firstText(map, "msg", "error_description", "message", "error");
            if (!raw.isBlank()) {
                return friendly(raw);
            }
        } catch (RuntimeException ignored) {
            return fallback;
        }
        return fallback;
    }

    private static String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = text(map, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String friendly(String raw) {
        String text = raw.toLowerCase();
        if (text.contains("invalid format") || text.contains("unable to validate email") || text.contains("invalid email")) {
            return "Use a valid email address.";
        }
        if (text.contains("invalid login") || text.contains("invalid_credentials") || text.contains("invalid grant")) {
            return "Email or password is wrong.";
        }
        if (text.contains("not confirmed")) {
            return "Confirm your email before signing in.";
        }
        if (text.contains("already") && (text.contains("registered") || text.contains("exists"))) {
            return "An account already uses that email.";
        }
        if (text.contains("password") && (text.contains("at least") || text.contains("short") || text.contains("weak") || text.contains("characters"))) {
            return "Use 8 characters or more for the password.";
        }
        if (text.contains("redirect")) {
            return "Add this site to the Supabase redirect URLs, then try again.";
        }
        return raw;
    }

    private record Result(int status, String body) {
    }

    public record AuthUser(String id, String email, String name) {
    }

    public record AuthSession(String accessToken, String refreshToken, int expiresIn, AuthUser user) {
    }

    public record SignUp(AuthSession session, AuthUser user) {
    }
}
