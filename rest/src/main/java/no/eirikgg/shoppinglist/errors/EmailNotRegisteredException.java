package no.eirikgg.shoppinglist.errors;

import java.util.UUID;

public class EmailNotRegisteredException extends Exception {
    private String email;

    public EmailNotRegisteredException(String email, String message) {
        super(message);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
