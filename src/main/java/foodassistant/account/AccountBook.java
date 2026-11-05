package foodassistant.account;

import foodassistant.model.Account;
import foodassistant.server.Json;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates accounts, checks passwords, and remembers who is signed in.
 * The rows live in Supabase. Passwords are stored only as a hash.
 */
public final class AccountBook {
    public static final String SIGN_IN_FIRST = "Sign in first.";

    private final SupabaseDatabase database;
    private List<StoredAccount> accounts;
    private List<StoredSession> sessions;

    private AccountBook(SupabaseDatabase database, List<StoredAccount> accounts, List<StoredSession> sessions) {
        this.database = database;
        this.accounts = accounts;
        this.sessions = sessions;
    }

    public static AccountBook load(SupabaseDatabase database) {
        List<StoredAccount> accounts = new ArrayList<>();
        for (SupabaseDatabase.AccountRow row : database.accounts()) {
            accounts.add(new StoredAccount(
                    row.id(), row.name(), row.email(), row.salt(), row.passwordHash(), row.birthday()));
        }
        List<StoredSession> sessions = new ArrayList<>();
        for (SupabaseDatabase.SessionRow row : database.sessions()) {
            sessions.add(new StoredSession(row.token(), row.accountId()));
        }
        return new AccountBook(database, List.copyOf(accounts), List.copyOf(sessions));
    }

    public synchronized SignedIn register(String name, String email, String password) {
        String cleanName = cleanName(name);
        String cleanEmail = cleanEmail(email);
        checkPassword(password);
        for (StoredAccount account : accounts) {
            if (account.email.equals(cleanEmail)) {
                throw new IllegalArgumentException("An account already uses that email.");
            }
        }
        StoredAccount created = new StoredAccount(
                "u:" + UUID.randomUUID(),
                cleanName,
                cleanEmail,
                Passwords.newSalt(),
                "",
                "");
        created = created.withHash(Passwords.hash(password, created.salt));
        List<StoredAccount> next = new ArrayList<>(accounts);
        next.add(created);
        database.insertAccount(new SupabaseDatabase.AccountRow(
                created.id, created.name, created.email, created.salt, created.passwordHash, created.birthday));
        accounts = List.copyOf(next);
        return openSession(created);
    }

    public synchronized SignedIn signIn(String email, String password) {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        StoredAccount match = null;
        for (StoredAccount account : accounts) {
            if (account.email.equals(cleanEmail)) {
                match = account;
                break;
            }
        }
        if (match == null || password == null || !Passwords.matches(password, match.salt, match.passwordHash)) {
            throw new SignInRequired("Email or password is wrong.");
        }
        return openSession(match);
    }

    public synchronized void signOut(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        database.deleteSession(token);
        List<StoredSession> next = new ArrayList<>();
        for (StoredSession session : sessions) {
            if (!session.token.equals(token)) {
                next.add(session);
            }
        }
        sessions = List.copyOf(next);
    }

    public synchronized Account accountFor(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        for (StoredSession session : sessions) {
            if (!session.token.equals(token)) {
                continue;
            }
            for (StoredAccount account : accounts) {
                if (account.id.equals(session.accountId)) {
                    return account.view();
                }
            }
        }
        return null;
    }

    public synchronized Account updateProfile(String accountId, String name, String email, String birthday) {
        StoredAccount current = findAccount(accountId);
        String cleanName = cleanName(name);
        String cleanEmail = cleanEmail(email);
        String cleanBirthday = cleanBirthday(birthday);
        for (StoredAccount account : accounts) {
            if (!account.id.equals(accountId) && account.email.equals(cleanEmail)) {
                throw new IllegalArgumentException("An account already uses that email.");
            }
        }
        StoredAccount updated = new StoredAccount(
                current.id, cleanName, cleanEmail, current.salt, current.passwordHash, cleanBirthday);
        database.updateProfile(current.id, cleanName, cleanEmail, cleanBirthday);
        replace(updated);
        return updated.view();
    }

    public synchronized void changePassword(String accountId, String currentPassword, String newPassword) {
        StoredAccount current = findAccount(accountId);
        if (currentPassword == null || !Passwords.matches(currentPassword, current.salt, current.passwordHash)) {
            throw new IllegalArgumentException("Current password is wrong.");
        }
        checkPassword(newPassword);
        String salt = Passwords.newSalt();
        String hash = Passwords.hash(newPassword, salt);
        database.updatePassword(current.id, salt, hash);
        replace(new StoredAccount(current.id, current.name, current.email, salt, hash, current.birthday));
    }

    public static String signedInResponse(SignedIn signedIn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", signedIn.token());
        payload.put("account", accountMap(signedIn.account()));
        return Json.stringify(payload);
    }

    public static String accountResponse(Account account) {
        return Json.stringify(Map.of("account", accountMap(account)));
    }

    private SignedIn openSession(StoredAccount account) {
        StoredSession session = new StoredSession(Passwords.newToken(), account.id);
        database.insertSession(session.token, account.id);
        List<StoredSession> next = new ArrayList<>(sessions);
        next.add(session);
        sessions = List.copyOf(next);
        return new SignedIn(session.token, account.view());
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
        StoredAccount withHash(String hash) {
            return new StoredAccount(id, name, email, salt, hash, birthday);
        }

        Account view() {
            return new Account(id, name, email, birthday);
        }
    }

    private record StoredSession(String token, String accountId) {
    }

    public record SignedIn(String token, Account account) {
    }
}
