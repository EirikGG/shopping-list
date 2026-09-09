package no.eirikgg.tgg.db.user;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Email {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String email;

    public Email(String email) {
        this.email = email;
    }


    public String getEmail() {
        return email;
    }

}
