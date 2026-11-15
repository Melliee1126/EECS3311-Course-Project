package foodassistant.instant;

import foodassistant.account.AccountBook;
import foodassistant.account.SupabaseDatabase;
import foodassistant.server.Json;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Stores a photo sent to friends, and lists those photos plus the ones friends sent back. */
public final class InstantBoard {
    private static final int MAX_PHOTO = 200_000;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    private final SupabaseDatabase database;
    private final AccountBook accounts;

    public InstantBoard(SupabaseDatabase database, AccountBook accounts) {
        this.database = database;
        this.accounts = accounts;
    }

    public void share(String accountId, Map<String, Object> body) {
        String photo = photo(body);
        List<String> recipients = recipients(accountId, body.get("friendIds"));
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("Choose a friend.");
        }
        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        database.insertInstant(id, accountId, photo, createdAt);
        try {
            database.insertReceipts(id, recipients);
        } catch (RuntimeException exception) {
            database.deleteInstant(id);
            throw exception;
        }
    }

    public String inboxResponse(String accountId) {
        List<String> ids = new ArrayList<>();
        collect(ids, database.instantIdsByAuthor(accountId));
        collect(ids, database.instantIdsFor(accountId));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> avatars = new LinkedHashMap<>();
        for (SupabaseDatabase.InstantRow instant : database.instantsByIds(ids)) {
            String authorId = instant.accountId();
            if (!names.containsKey(authorId)) {
                names.put(authorId, accounts.displayName(authorId));
                avatars.put(authorId, accounts.avatarOf(authorId));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", instant.id());
            row.put("authorId", authorId);
            row.put("author", names.get(authorId));
            row.put("avatar", avatars.get(authorId));
            row.put("photo", instant.photo());
            row.put("when", when(instant.createdAt()));
            rows.add(row);
        }
        return Json.stringify(Map.of("instants", rows));
    }

    private List<String> recipients(String accountId, Object value) {
        List<String> friends = accounts.friendIds(accountId);
        List<String> chosen = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return chosen;
        }
        for (Object item : list) {
            if (!(item instanceof String text)) {
                continue;
            }
            String id = text.trim();
            if (id.isEmpty() || id.equals(accountId) || chosen.contains(id) || !friends.contains(id)) {
                continue;
            }
            chosen.add(id);
            if (chosen.size() == 50) {
                break;
            }
        }
        return chosen;
    }

    private static String photo(Map<String, Object> body) {
        Object value = body.get("photo");
        String photo = value instanceof String text ? text.trim() : "";
        if (!photo.startsWith("data:image/jpeg;base64,")
                && !photo.startsWith("data:image/png;base64,")
                && !photo.startsWith("data:image/webp;base64,")) {
            throw new IllegalArgumentException("Use a photo.");
        }
        if (photo.length() > MAX_PHOTO) {
            throw new IllegalArgumentException("That photo is too large.");
        }
        return photo;
    }

    private static void collect(List<String> ids, List<String> more) {
        for (String id : more) {
            if (ids.size() == 200) {
                return;
            }
            if (safeId(id) && !ids.contains(id)) {
                ids.add(id);
            }
        }
    }

    private static boolean safeId(String id) {
        if (id == null || id.length() < 32 || id.length() > 40) {
            return false;
        }
        for (int index = 0; index < id.length(); index += 1) {
            char ch = id.charAt(index);
            boolean ok = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F') || ch == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String when(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return "";
        }
        String text = createdAt.trim();
        if (text.length() > 10 && text.charAt(10) == ' ') {
            text = text.substring(0, 10) + "T" + text.substring(11);
        }
        try {
            return WHEN.format(Instant.parse(text));
        } catch (DateTimeParseException exception) {
            return "";
        }
    }
}
