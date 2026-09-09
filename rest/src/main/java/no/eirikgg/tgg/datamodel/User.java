package no.eirikgg.tgg.datamodel;

import com.fasterxml.jackson.annotation.JsonProperty;

public class User {


    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Person person;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Authorization authorization;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Email email;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private PhoneNumber phonenumber;


    public User(Person person, Authorization authorization, Email email, PhoneNumber phonenumber) {
        this.person = person;
        this.authorization = authorization;
        this.email = email;
        this.phonenumber = phonenumber;
    }


    public Person getPerson() {
        return person;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public Email getEmail() {
        return email;
    }

    public PhoneNumber getPhonenumber() {
        return phonenumber;
    }
}
