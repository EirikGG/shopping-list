package no.eirikgg.shoppinglist.errors;

public class StatusAlreadyChangedException extends Exception {

    public StatusAlreadyChangedException(String message) {
        super(message);
    }
}
