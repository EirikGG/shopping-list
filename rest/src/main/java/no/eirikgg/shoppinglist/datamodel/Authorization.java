package no.eirikgg.shoppinglist.datamodel;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.eirikgg.shoppinglist.db.DbIdentity;

public class Authorization extends DbIdentity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String email;

    @JsonProperty(value = "password", access = JsonProperty.Access.READ_ONLY)
    private String passwordHash;

    public Authorization(Integer id, String email, String passwordHash) {
        super(id);
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Authorization(String email, String passwordHash) {
        this(null, email, passwordHash);
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    @JsonGetter("password")
    public String getHiddenPassword() {
        return "*********";
    }
}
