package foodassistant.account;

/** Profile photos, stored in Supabase so friends can see the picture the person chose. */
public final class AvatarStore {
    private final SupabaseDatabase database;

    public AvatarStore(SupabaseDatabase database) {
        this.database = database;
    }

    public String get(String accountId) {
        return database.avatar(accountId);
    }

    public void save(String accountId, String avatar) {
        database.saveAvatar(accountId, avatar);
    }
}
