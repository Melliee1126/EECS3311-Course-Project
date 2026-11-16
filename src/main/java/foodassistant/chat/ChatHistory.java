package foodassistant.chat;

import foodassistant.account.SupabaseDatabase;
import foodassistant.server.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stores the signed-in account's chats so another browser can open them. */
public final class ChatHistory {
    public static final int LIMIT = 20;
    private static final int MAX_MESSAGES = 40;
    private static final int MAX_TEXT = 8_000;
    private static final int MAX_PICKS = 8;

    private final SupabaseDatabase database;

    public ChatHistory(SupabaseDatabase database) {
        this.database = database;
    }

    public String list(String accountId) {
        return Json.stringify(Map.of("sessions", sessions(database.chatSessions(accountId))));
    }

    public String replace(String accountId, Object body) {
        if (!(body instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Send the chat sessions.");
        }
        Object value = raw.get("sessions");
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Send the chat sessions.");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> row = row(item);
            if (row != null) {
                rows.add(row);
            }
            if (rows.size() == LIMIT) {
                break;
            }
        }
        database.upsertChatSessions(accountId, rows);
        return list(accountId);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sessions(String json) {
        Object parsed = Json.parse(json);
        List<Map<String, Object>> sessions = new ArrayList<>();
        if (!(parsed instanceof List<?> list)) {
            return sessions;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> session = session((Map<String, Object>) raw, true);
            if (session != null) {
                sessions.add(session);
            }
            if (sessions.size() == LIMIT) {
                break;
            }
        }
        return sessions;
    }

    private static Map<String, Object> row(Object item) {
        if (!(item instanceof Map<?, ?> raw)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> session = session((Map<String, Object>) raw, false);
        if (session == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", session.get("id"));
        row.put("title", session.get("title"));
        row.put("updated_at", session.get("updatedAt"));
        row.put("messages", session.get("messages"));
        return row;
    }

    private static Map<String, Object> session(Map<String, Object> raw, boolean stored) {
        String id = text(raw, "id");
        if (!safeId(id)) {
            return null;
        }
        List<Map<String, Object>> messages = messages(raw.get("messages"));
        if (messages.isEmpty()) {
            return null;
        }
        String title = text(raw, "title").trim();
        if (title.isEmpty()) {
            title = "Chat";
        }
        if (title.length() > 48) {
            title = title.substring(0, 48);
        }
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", id);
        session.put("title", title);
        session.put("updatedAt", timestamp(stored ? raw.get("updated_at") : raw.get("updatedAt")));
        session.put("messages", messages);
        return session;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messages(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            try {
                value = Json.parse(text);
            } catch (RuntimeException exception) {
                return List.of();
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return messages;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> message = message((Map<String, Object>) raw);
            if (message != null) {
                messages.add(message);
            }
            if (messages.size() == MAX_MESSAGES) {
                break;
            }
        }
        return messages;
    }

    private static Map<String, Object> message(Map<String, Object> raw) {
        String role = text(raw, "role");
        if (!"user".equals(role) && !"assistant".equals(role)) {
            return null;
        }
        String body = text(raw, "text");
        if (body.length() > MAX_TEXT) {
            body = body.substring(0, MAX_TEXT);
        }
        List<Map<String, Object>> picks = picks(raw.get("picks"));
        if (body.isBlank() && picks.isEmpty()) {
            return null;
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("text", body);
        message.put("picks", picks);
        return message;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> picks(Object value) {
        List<Map<String, Object>> picks = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return picks;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> place = place((Map<String, Object>) raw);
            if (place != null) {
                picks.add(place);
            }
            if (picks.size() == MAX_PICKS) {
                break;
            }
        }
        return picks;
    }

    private static Map<String, Object> place(Map<String, Object> raw) {
        String restId = text(raw, "restId");
        String name = text(raw, "name");
        if (restId.isBlank() || name.isBlank() || restId.length() > 200 || name.length() > 200) {
            return null;
        }
        String status = text(raw, "status");
        if (!"saved".equals(status) && !"visited".equals(status)) {
            status = "discovered";
        }
        Map<String, Object> place = new LinkedHashMap<>();
        place.put("restId", restId);
        place.put("name", name);
        place.put("cuisine", clip(text(raw, "cuisine"), 80));
        place.put("address", clip(text(raw, "address"), 200));
        place.put("latitude", coordinate(raw.get("latitude")));
        place.put("longitude", coordinate(raw.get("longitude")));
        place.put("rating", number(raw.get("rating")));
        place.put("priceRange", clip(text(raw, "priceRange"), 16));
        place.put("priceSpan", clip(text(raw, "priceSpan"), 40));
        place.put("status", status);
        place.put("tags", words(raw.get("tags"), 12, 40));
        place.put("photo", clip(text(raw, "photo"), 2_000));
        place.put("explanation", clip(text(raw, "explanation"), 800));
        place.put("matchReasons", words(raw.get("matchReasons"), 8, 160));
        return place;
    }

    private static List<String> words(Object value, int max, int length) {
        List<String> words = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return words;
        }
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                continue;
            }
            words.add(clip(text.trim(), length));
            if (words.size() == max) {
                break;
            }
        }
        return words;
    }

    private static boolean safeId(String id) {
        if (id.isBlank() || id.length() > 80) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char ch = id.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static long timestamp(Object value) {
        double number = number(value);
        if (number <= 0) {
            return System.currentTimeMillis();
        }
        return (long) number;
    }

    private static Double coordinate(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double coordinate = number.doubleValue();
        if (coordinate < -180 || coordinate > 180) {
            return null;
        }
        return coordinate;
    }

    private static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            return 0;
        }
        return number.doubleValue();
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String text ? text : "";
    }
}
