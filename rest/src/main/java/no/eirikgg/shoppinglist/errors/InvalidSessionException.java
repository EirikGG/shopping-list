package no.eirikgg.shoppinglist.errors;

import java.util.UUID;

public class InvalidSessionException extends Exception {

    private UUID sessionId;

    public InvalidSessionException(UUID sessionId, String message) {
        super(message);
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
