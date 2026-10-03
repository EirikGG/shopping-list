package no.eirikgg.shoppinglist.datamodel;

import com.fasterxml.jackson.annotation.JsonProperty;

public class User {


    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Person person;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Authorization authorization;

    public User(Person person, Authorization authorization) {
        this.person = person;
        this.authorization = authorization;
    }


    public Person getPerson() {
        return person;
    }

    public Authorization getAuthorization() {
        return authorization;
    }
}
