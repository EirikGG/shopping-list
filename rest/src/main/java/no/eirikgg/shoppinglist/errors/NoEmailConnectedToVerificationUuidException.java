package no.eirikgg.shoppinglist.errors;

import java.util.UUID;

public class NoEmailConnectedToVerificationUuidException extends Exception {
    private UUID verificationUuid;

    public NoEmailConnectedToVerificationUuidException(UUID verificationUuid, String message) {
        super(message);
        this.verificationUuid = verificationUuid;
    }

    public UUID getVerificationUuid() {
        return verificationUuid;
    }
}
