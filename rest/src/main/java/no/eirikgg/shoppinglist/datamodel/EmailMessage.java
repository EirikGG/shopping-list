package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;
import java.util.UUID;

public class EmailMessage extends DbIdentity {

    public enum Status {
        CREATED,
        PROCESSING,
        SENDT,
        // Sending threw. Not retried; set it back to CREATED to send it again.
        FAILED
    }

    private UUID sessionId;
    private String address;
    private Status status;
    private String subject;
    private String body;
    private LocalDateTime created;
    private LocalDateTime updated;
    private LocalDateTime sent;

    public EmailMessage(
        Integer id,
        UUID sessionId,
        String address,
        Status status,
        String subject,
        String body,
        LocalDateTime created,
        LocalDateTime updated,
        LocalDateTime sent
    ) {
        super(id);
        this.sessionId = sessionId;
        this.address = address;
        this.status = status;
        this.subject = subject;
        this.body = body;
        this.created = created;
        this.updated = updated;
        this.sent = sent;
    }

    public EmailMessage(
        UUID sessionId,
        String address,
        Status status,
        String subject,
        String body,
        LocalDateTime created,
        LocalDateTime updated,
        LocalDateTime sent
    ) {
        this(null, sessionId, address, status, subject, body, created, updated, sent);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getAddress() {
        return address;
    }

    public Status getStatus() {
        return status;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public LocalDateTime getSent() {
        return sent;
    }
}
