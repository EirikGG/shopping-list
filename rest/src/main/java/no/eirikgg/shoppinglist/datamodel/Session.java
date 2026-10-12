package no.eirikgg.shoppinglist.datamodel;

import java.util.UUID;

public class Session {

    private UUID sessionId;
    private User user;

    public Session(UUID sessionId, User user) {
        this.sessionId = sessionId;
        this.user = user;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public User getUser() {
        return user;
    }
}
