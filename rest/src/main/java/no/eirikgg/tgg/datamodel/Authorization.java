package no.eirikgg.tgg.datamodel;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.eirikgg.tgg.db.DbIdentity;

public class Authorization extends DbIdentity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String username;

    @JsonProperty(value = "password", access = JsonProperty.Access.READ_ONLY)
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

    @JsonGetter("password")
    public String getHiddenPassword() {
        return "*********";
    }
}
