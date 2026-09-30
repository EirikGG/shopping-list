package no.eirikgg.shoppinglist.datamodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class Email {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String email;

    @JsonIgnore
    private UUID verificationUuid;

    public Email(String email, UUID verificationUuid) {
        this.email = email;
        this.verificationUuid = verificationUuid;
    }


    public String getEmail() {
        return email;
    }

    public UUID getVerificationUuid() {return verificationUuid;}
}
