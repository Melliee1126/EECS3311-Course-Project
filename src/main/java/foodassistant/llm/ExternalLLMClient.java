package foodassistant.llm;

import foodassistant.server.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The vendor call. It only knows how to send one prompt and read the text back. */
public final class ExternalLLMClient {
    private static final URI COMPLETIONS = URI.create("https://api.openai.com/v1/chat/completions");

    private final String apiKey;
    private final String model;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public ExternalLLMClient(String apiKey, String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();
    }

    public String sendRequest(String prompt) {
        return complete("", List.of(Map.of("role", "user", "content", prompt == null ? "" : prompt)));
    }

    public String complete(String system, List<Map<String, String>> messages) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Add OPENAI_API_KEY to supabase.local.");
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            payload.add(Map.of("role", "system", "content", system));
        }
        if (messages != null) {
            for (Map<String, String> message : messages) {
                String role = message.get("role");
                if (!"system".equals(role) && !"assistant".equals(role) && !"user".equals(role)) {
                    role = "user";
                }
                String content = message.get("content");
                payload.add(Map.of("role", role, "content", content == null ? "" : content));
            }
        }
        if (payload.isEmpty()) {
            throw new IllegalStateException("The language model did not answer.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.6);
        body.put("max_tokens", 700);
        body.put("messages", payload);
        HttpRequest request = HttpRequest.newBuilder(COMPLETIONS)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 401) {
            throw new IllegalStateException("The language model rejected the API key in supabase.local.");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("The language model did not answer.");
        }
        String text = content(response.body());
        if (text.isBlank()) {
            throw new IllegalStateException("The language model did not answer.");
        }
        return text;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The language model did not answer.");
        } catch (IOException exception) {
            throw new IllegalStateException("The language model did not answer.");
        }
    }

    private static String content(String json) {
        try {
            Object parsed = Json.parse(json);
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
                return "";
            }
            if (!(choices.get(0) instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
                return "";
            }
            Object content = message.get("content");
            return content instanceof String text ? text.trim() : "";
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
