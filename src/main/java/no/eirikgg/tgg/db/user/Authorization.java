package no.eirikgg.tgg.db.user;

import no.eirikgg.tgg.db.DbIdentity;

public class Authorization extends DbIdentity {
    private String username;
    private String passwordHash;

    public Authorization(Integer id, String username, String passwordHash) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Authorization(String username, String passwordHash) {
        this(null, username, passwordHash);
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
