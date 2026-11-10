package foodassistant.account;

import foodassistant.model.Account;
import foodassistant.review.ReviewPhotos;
import foodassistant.server.Json;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Profiles for people who sign in through Supabase Auth.
 * Supabase stores the password, sends confirmation and reset mail, and owns the session.
 */
public final class AccountBook {
    public static final String SIGN_IN_FIRST = "Sign in first.";
    private static final String CONFIRM_EMAIL = "Check your email to confirm your account, then sign in.";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    private final SupabaseDatabase database;
    private final SupabaseAuth auth;
    private final FriendStore friends;
    private final AvatarStore avatars;
    private List<StoredAccount> accounts;

    private AccountBook(SupabaseDatabase database, List<StoredAccount> accounts, FriendStore friends, AvatarStore avatars) {
        this.database = database;
        this.auth = database.auth();
        this.friends = friends;
        this.avatars = avatars;
        this.accounts = accounts;
    }

    public static AccountBook load(SupabaseDatabase database) {
        List<StoredAccount> accounts = new ArrayList<>();
        for (SupabaseDatabase.AccountRow row : database.accounts()) {
            accounts.add(new StoredAccount(
                    row.id(), row.name(), row.email(), row.salt(), row.passwordHash(), row.birthday()));
        }
        return new AccountBook(
                database,
                List.copyOf(accounts),
                new FriendStore(database),
                new AvatarStore(database));
    }

    public synchronized SignedIn register(String name, String email, String password, String redirectTo) {
        String cleanName = cleanName(name);
        String cleanEmail = cleanEmail(email);
        checkPassword(password);
        SupabaseAuth.SignUp created = auth.signUp(cleanEmail, password, cleanName, redirectTo);
        ensureProfile(created.user());
        if (created.session() == null) {
            throw new IllegalArgumentException(CONFIRM_EMAIL);
        }
        return signedIn(created.session());
    }

    public synchronized SignedIn signIn(String email, String password) {
        if (password == null || password.isBlank()) {
            throw new SignInRequired("Email or password is wrong.");
        }
        return signedIn(auth.signIn(cleanEmail(email), password));
    }

    public synchronized SignedIn refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SignInRequired(SIGN_IN_FIRST);
        }
        return signedIn(auth.refresh(refreshToken));
    }

    public void signOut(String token) {
        auth.signOut(token);
    }

    public synchronized void recover(String email, String redirectTo) {
        auth.recover(cleanEmail(email), redirectTo);
    }

    public synchronized Account findByEmail(String accountId, String email) {
        String cleanEmail = cleanEmail(email);
        StoredAccount self = findAccount(accountId);
        if (self.email.equals(cleanEmail)) {
            throw new IllegalArgumentException("That's your own account.");
        }
        for (StoredAccount account : accounts) {
            if (account.email.equals(cleanEmail)) {
                return account.view();
            }
        }
        throw new IllegalArgumentException("No account uses that email.");
    }

    public synchronized FriendCard lookupFriend(String accountId, String email) {
        return card(accountId, stored(findByEmail(accountId, email).getId()));
    }

    public synchronized FriendCard requestFriend(String accountId, String friendId) {
        if (accountId.equals(friendId)) {
            throw new IllegalArgumentException("That's your own account.");
        }
        StoredAccount friend = findAccount(friendId);
        friends.request(accountId, friend.id);
        return card(accountId, friend);
    }

    public synchronized void respond(String accountId, String fromId, boolean accept) {
        if (accept) {
            friends.accept(accountId, fromId);
        } else {
            friends.reject(accountId, fromId);
        }
    }

    public synchronized void cancelRequest(String accountId, String friendId) {
        friends.cancel(accountId, friendId);
    }

    public synchronized void saveAvatar(String accountId, String avatar) {
        if (avatar == null || avatar.isBlank()) {
            avatars.save(accountId, "");
            return;
        }
        if (!avatar.startsWith("data:image/") || avatar.length() > 120_000) {
            throw new IllegalArgumentException("That photo is too large.");
        }
        avatars.save(accountId, avatar);
    }

    public synchronized FriendCircle circle(String accountId) {
        return new FriendCircle(
                cards(accountId, friends.friendIds(accountId)),
                cards(accountId, friends.incomingIds(accountId)),
                cards(accountId, friends.outgoingIds(accountId)),
                posts(accountId));
    }

    public Account accountFor(String token) {
        SupabaseAuth.AuthUser user = auth.user(token);
        if (user == null) {
            return null;
        }
        synchronized (this) {
            return ensureProfile(user).view();
        }
    }

    public synchronized Account updateProfile(String accessToken, String name, String email, String birthday) {
        SupabaseAuth.AuthUser user = auth.user(accessToken);
        if (user == null) {
            throw new SignInRequired(SIGN_IN_FIRST);
        }
        StoredAccount current = ensureProfile(user);
        String cleanName = cleanName(name);
        String cleanEmail = cleanEmail(email);
        String cleanBirthday = cleanBirthday(birthday);
        boolean emailChanged = !current.email.equals(cleanEmail);
        if (emailChanged) {
            for (StoredAccount account : accounts) {
                if (!account.id.equals(current.id) && account.email.equals(cleanEmail)) {
                    throw new IllegalArgumentException("An account already uses that email.");
                }
            }
            if (!auth.updateEmail(accessToken, cleanEmail)) {
                StoredAccount updated = new StoredAccount(
                        current.id, cleanName, current.email, current.salt, current.passwordHash, cleanBirthday);
                database.updateProfile(updated.id, updated.name, updated.email, updated.birthday);
                replace(updated);
                throw new IllegalArgumentException("Confirm the new email from your inbox.");
            }
        }
        StoredAccount updated = new StoredAccount(
                current.id, cleanName, cleanEmail, current.salt, current.passwordHash, cleanBirthday);
        database.updateProfile(updated.id, updated.name, updated.email, updated.birthday);
        replace(updated);
        return updated.view();
    }

    public synchronized Account changePassword(String accessToken, String currentPassword, String newPassword) {
        checkPassword(newPassword);
        SupabaseAuth.AuthUser user = auth.user(accessToken);
        if (user == null) {
            throw new SignInRequired(SIGN_IN_FIRST);
        }
        if (currentPassword != null) {
            try {
                auth.signIn(user.email(), currentPassword);
            } catch (SignInRequired exception) {
                throw new IllegalArgumentException("Current password is wrong.");
            }
        }
        auth.updatePassword(accessToken, newPassword);
        return ensureProfile(user).view();
    }

    public static String signedInResponse(SignedIn signedIn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", signedIn.token());
        payload.put("refreshToken", signedIn.refreshToken());
        payload.put("expiresIn", signedIn.expiresIn());
        payload.put("account", accountMap(signedIn.account()));
        return Json.stringify(payload);
    }

    public static String accountResponse(Account account) {
        return Json.stringify(Map.of("account", accountMap(account)));
    }

    public static String lookupResponse(FriendCard card) {
        return Json.stringify(Map.of("account", cardMap(card)));
    }

    public static String circleResponse(FriendCircle circle) {
        return Json.stringify(circleMap(circle));
    }

    public static String requestResponse(FriendCard card, FriendCircle circle) {
        Map<String, Object> payload = circleMap(circle);
        payload.put("account", cardMap(card));
        return Json.stringify(payload);
    }

    private static Map<String, Object> circleMap(FriendCircle circle) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("friends", cardList(circle.friends()));
        payload.put("incoming", cardList(circle.incoming()));
        payload.put("outgoing", cardList(circle.outgoing()));
        payload.put("posts", postList(circle.posts()));
        return payload;
    }

    private static List<Object> cardList(List<FriendCard> cards) {
        List<Object> list = new ArrayList<>();
        for (FriendCard card : cards) {
            list.add(cardMap(card));
        }
        return list;
    }

    private static List<Object> postList(List<FriendPost> posts) {
        List<Object> list = new ArrayList<>();
        for (FriendPost post : posts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", post.id());
            row.put("author", post.author());
            row.put("avatar", post.avatar());
            row.put("when", post.when());
            row.put("restaurant", post.restaurant());
            row.put("restId", post.restId());
            row.put("address", post.address());
            row.put("latitude", post.latitude());
            row.put("longitude", post.longitude());
            row.put("rating", post.rating());
            row.put("tags", post.tags());
            row.put("body", post.body());
            row.put("photo", post.photos().isEmpty() ? "" : post.photos().get(0));
            row.put("photos", post.photos());
            list.add(row);
        }
        return list;
    }

    private static Map<String, Object> cardMap(FriendCard card) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("id", card.id());
        person.put("name", card.name());
        person.put("handle", card.handle());
        person.put("reviewCount", card.reviewCount());
        person.put("status", card.status());
        person.put("added", "added".equals(card.status()));
        person.put("avatar", card.avatar());
        return person;
    }

    private List<FriendCard> cards(String accountId, List<String> ids) {
        List<FriendCard> cards = new ArrayList<>();
        for (String friendId : ids) {
            StoredAccount friend = findOptional(friendId);
            if (friend != null) {
                cards.add(card(accountId, friend));
            }
        }
        return cards;
    }

    private List<FriendPost> posts(String accountId) {
        List<TimedPost> timed = new ArrayList<>();
        for (String friendId : friends.friendIds(accountId)) {
            StoredAccount friend = findOptional(friendId);
            if (friend == null) {
                continue;
            }
            for (SupabaseDatabase.PublicReview review : database.publicReviews(friendId)) {
                timed.add(new TimedPost(review.createdAt(), new FriendPost(
                        review.id(),
                        friend.name,
                        avatars.get(friend.id),
                        when(review.createdAt()),
                        review.restaurant(),
                        review.restId(),
                        review.address(),
                        review.latitude(),
                        review.longitude(),
                        review.rating(),
                        review.tags(),
                        review.body(),
                        ReviewPhotos.decode(review.photo()))));
            }
        }
        timed.sort(Comparator.comparing(TimedPost::createdAt).reversed());
        List<FriendPost> posts = new ArrayList<>();
        for (TimedPost post : timed) {
            posts.add(post.post());
        }
        return posts;
    }

    private FriendCard card(String accountId, StoredAccount friend) {
        return new FriendCard(
                friend.id,
                friend.name,
                handle(friend.email),
                database.reviewCount(friend.id),
                relation(accountId, friend.id),
                avatars.get(friend.id));
    }

    private String relation(String accountId, String otherId) {
        if (friends.areFriends(accountId, otherId)) {
            return "added";
        }
        if (friends.hasOutgoing(accountId, otherId)) {
            return "pending";
        }
        if (friends.hasIncoming(accountId, otherId)) {
            return "incoming";
        }
        return "none";
    }

    private static String when(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return "";
        }
        try {
            Instant instant = Instant.parse(createdAt);
            return WHEN.format(instant);
        } catch (DateTimeParseException exception) {
            return "";
        }
    }

    private StoredAccount stored(String accountId) {
        StoredAccount account = findOptional(accountId);
        if (account == null) {
            throw new IllegalArgumentException("No account uses that email.");
        }
        return account;
    }

    private StoredAccount findOptional(String accountId) {
        for (StoredAccount account : accounts) {
            if (account.id.equals(accountId)) {
                return account;
            }
        }
        return null;
    }

    private static String handle(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        String slug = local.replaceAll("[^a-zA-Z0-9._]", "");
        return slug.isEmpty() ? "user" : slug;
    }

    private SignedIn signedIn(SupabaseAuth.AuthSession session) {
        return new SignedIn(
                session.accessToken(),
                session.refreshToken(),
                session.expiresIn(),
                ensureProfile(session.user()).view());
    }

    private StoredAccount ensureProfile(SupabaseAuth.AuthUser user) {
        for (StoredAccount account : accounts) {
            if (!account.id.equals(user.id())) {
                continue;
            }
            if (!user.email().isBlank() && !account.email.equalsIgnoreCase(user.email())) {
                StoredAccount updated = new StoredAccount(
                        account.id, account.name, user.email(), account.salt, account.passwordHash, account.birthday);
                database.updateProfile(updated.id, updated.name, updated.email, updated.birthday);
                replace(updated);
                return updated;
            }
            return account;
        }
        String name = user.name() == null ? "" : user.name().trim();
        if (name.isEmpty()) {
            int at = user.email().indexOf('@');
            name = at > 0 ? user.email().substring(0, at) : "Friend";
        }
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        StoredAccount created = new StoredAccount(user.id(), name, user.email(), "supabase", "supabase", "");
        try {
            database.insertAccount(new SupabaseDatabase.AccountRow(
                    created.id, created.name, created.email, created.salt, created.passwordHash, created.birthday));
        } catch (RuntimeException exception) {
            StoredAccount existing = findByAddress(user.email());
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
        List<StoredAccount> next = new ArrayList<>(accounts);
        next.add(created);
        accounts = List.copyOf(next);
        return created;
    }

    private StoredAccount findByAddress(String email) {
        for (StoredAccount account : accounts) {
            if (account.email.equalsIgnoreCase(email)) {
                return account;
            }
        }
        return null;
    }

    private static Map<String, Object> accountMap(Account account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("name", account.getName());
        map.put("email", account.getEmail());
        map.put("birthday", account.getBirthday());
        return map;
    }

    private StoredAccount findAccount(String accountId) {
        for (StoredAccount account : accounts) {
            if (account.id.equals(accountId)) {
                return account;
            }
        }
        throw new IllegalArgumentException("Account was not found.");
    }

    private void replace(StoredAccount updated) {
        List<StoredAccount> next = new ArrayList<>();
        for (StoredAccount account : accounts) {
            next.add(account.id.equals(updated.id) ? updated : account);
        }
        accounts = List.copyOf(next);
    }

    private static String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Name your account.");
        }
        if (trimmed.length() > 40) {
            throw new IllegalArgumentException("Use 40 characters or fewer.");
        }
        return trimmed;
    }

    private static String cleanEmail(String email) {
        String trimmed = email == null ? "" : email.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Enter an email.");
        }
        int at = trimmed.indexOf('@');
        int dot = trimmed.lastIndexOf('.');
        if (at <= 0 || dot < at + 2 || dot >= trimmed.length() - 1 || trimmed.indexOf('@', at + 1) >= 0) {
            throw new IllegalArgumentException("That email is not valid.");
        }
        if (trimmed.length() > 80) {
            throw new IllegalArgumentException("Use 80 characters or fewer.");
        }
        return trimmed;
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Use 8 characters or more for the password.");
        }
        if (password.length() > 80) {
            throw new IllegalArgumentException("Use 80 characters or fewer.");
        }
    }

    private static String cleanBirthday(String birthday) {
        String trimmed = birthday == null ? "" : birthday.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(trimmed);
            if (date.isAfter(LocalDate.now()) || date.getYear() < 1900) {
                throw new IllegalArgumentException("Use a real birthday.");
            }
            return date.toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Use a real birthday.");
        }
    }

    private record StoredAccount(String id, String name, String email, String salt, String passwordHash, String birthday) {
        Account view() {
            return new Account(id, name, email, birthday);
        }
    }

    public record SignedIn(String token, String refreshToken, int expiresIn, Account account) {
    }

    public record FriendCard(String id, String name, String handle, int reviewCount, String status, String avatar) {
    }

    public record FriendPost(
            String id,
            String author,
            String avatar,
            String when,
            String restaurant,
            String restId,
            String address,
            Double latitude,
            Double longitude,
            int rating,
            List<String> tags,
            String body,
            List<String> photos) {
    }

    public record FriendCircle(
            List<FriendCard> friends,
            List<FriendCard> incoming,
            List<FriendCard> outgoing,
            List<FriendPost> posts) {
    }

    private record TimedPost(String createdAt, FriendPost post) {
    }
}
