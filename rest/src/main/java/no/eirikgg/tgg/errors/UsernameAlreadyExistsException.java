package no.eirikgg.tgg.errors;

public class UsernameAlreadyExistsException extends Exception {

    public UsernameAlreadyExistsException(String msg) {
        super(msg);
    }
}
