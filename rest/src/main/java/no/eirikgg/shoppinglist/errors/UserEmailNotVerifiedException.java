package no.eirikgg.shoppinglist.errors;

public class UserEmailNotVerifiedException extends Exception {

    private String email;

    public UserEmailNotVerifiedException(String email, String message) {
        super(message);
    }

    public String getEmail() {
        return email;
    }
}
