package no.eirikgg.shoppinglist.errors;

public class UsernameAlreadyExistsException extends Exception {

    public UsernameAlreadyExistsException(String msg) {
        super(msg);
    }
}
