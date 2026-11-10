package foodassistant.account;

import foodassistant.account.SupabaseDatabase.FriendLink;

import java.util.ArrayList;
import java.util.List;

/** Pending and accepted friend requests, stored in Supabase. */
public final class FriendStore {
    private final SupabaseDatabase database;

    public FriendStore(SupabaseDatabase database) {
        this.database = database;
    }

    public boolean areFriends(String accountId, String otherId) {
        FriendLink link = between(accountId, otherId);
        return link != null && "accepted".equals(link.status());
    }

    public boolean hasOutgoing(String accountId, String otherId) {
        FriendLink link = between(accountId, otherId);
        return link != null && "pending".equals(link.status()) && link.fromId().equals(accountId);
    }

    public boolean hasIncoming(String accountId, String otherId) {
        FriendLink link = between(accountId, otherId);
        return link != null && "pending".equals(link.status()) && link.toId().equals(accountId);
    }

    public List<String> friendIds(String accountId) {
        return ids(accountId, "accepted");
    }

    public List<String> incomingIds(String accountId) {
        List<String> ids = new ArrayList<>();
        for (FriendLink link : database.linksFor(accountId)) {
            if ("pending".equals(link.status()) && link.toId().equals(accountId)) {
                ids.add(link.fromId());
            }
        }
        return List.copyOf(ids);
    }

    public List<String> outgoingIds(String accountId) {
        List<String> ids = new ArrayList<>();
        for (FriendLink link : database.linksFor(accountId)) {
            if ("pending".equals(link.status()) && link.fromId().equals(accountId)) {
                ids.add(link.toId());
            }
        }
        return List.copyOf(ids);
    }

    /** Creates a pending request. A request already waiting the other way is accepted. */
    public void request(String from, String to) {
        FriendLink existing = between(from, to);
        if (existing != null) {
            if ("accepted".equals(existing.status()) || existing.fromId().equals(from)) {
                return;
            }
            accept(from, to);
            return;
        }
        database.insertFriendship(from, to, "pending");
    }

    public void accept(String accountId, String fromId) {
        FriendLink link = between(accountId, fromId);
        if (link == null || !"pending".equals(link.status()) || !link.fromId().equals(fromId) || !link.toId().equals(accountId)) {
            throw new IllegalArgumentException("That request was not found.");
        }
        database.updateFriendship(link.fromId(), link.toId(), "accepted");
    }

    public void reject(String accountId, String fromId) {
        FriendLink link = between(accountId, fromId);
        if (link == null || !"pending".equals(link.status()) || !link.fromId().equals(fromId) || !link.toId().equals(accountId)) {
            throw new IllegalArgumentException("That request was not found.");
        }
        database.deleteFriendship(link.fromId(), link.toId());
    }

    public void cancel(String accountId, String toId) {
        FriendLink link = between(accountId, toId);
        if (link == null || !"pending".equals(link.status()) || !link.fromId().equals(accountId) || !link.toId().equals(toId)) {
            throw new IllegalArgumentException("That request was not found.");
        }
        database.deleteFriendship(link.fromId(), link.toId());
    }

    private List<String> ids(String accountId, String status) {
        List<String> ids = new ArrayList<>();
        for (FriendLink link : database.linksFor(accountId)) {
            if (!status.equals(link.status())) {
                continue;
            }
            if (link.fromId().equals(accountId)) {
                ids.add(link.toId());
            } else if (link.toId().equals(accountId)) {
                ids.add(link.fromId());
            }
        }
        return List.copyOf(ids);
    }

    private FriendLink between(String accountId, String otherId) {
        for (FriendLink link : database.linksFor(accountId)) {
            boolean pair = (link.fromId().equals(accountId) && link.toId().equals(otherId))
                    || (link.fromId().equals(otherId) && link.toId().equals(accountId));
            if (pair) {
                return link;
            }
        }
        return null;
    }
}
