package no.eirikgg.shoppinglist.errors;

public class EmailAlreadyVerifiedException extends Exception {
    private String email;
    public EmailAlreadyVerifiedException(String email, String message) {
        super(message);
        this.email = email;
    }
}
