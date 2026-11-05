package foodassistant.model;

/**
 * A person who can sign in. The password stays in {@code foodassistant.account}
 * and is never part of this object.
 */
public final class Account {
    private final String id;
    private final String name;
    private final String email;
    private final String birthday;

    public Account(String id, String name, String email, String birthday) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.birthday = birthday == null ? "" : birthday;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getBirthday() {
        return birthday;
    }
}
