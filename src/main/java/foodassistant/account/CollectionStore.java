package foodassistant.account;

import foodassistant.collection.CollectionLibrary;

import java.util.HashMap;
import java.util.Map;

/** Each account's collections, loaded from Supabase. */
public final class CollectionStore {
    private final SupabaseDatabase database;
    private final Map<String, CollectionLibrary> open = new HashMap<>();

    public CollectionStore(SupabaseDatabase database) {
        this.database = database;
    }

    public synchronized CollectionLibrary forAccount(String accountId) {
        CollectionLibrary existing = open.get(accountId);
        if (existing != null) {
            return existing;
        }
        CollectionLibrary loaded = CollectionLibrary.load(database, accountId);
        open.put(accountId, loaded);
        return loaded;
    }
}
