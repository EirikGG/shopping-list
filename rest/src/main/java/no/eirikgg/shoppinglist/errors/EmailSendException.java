package no.eirikgg.shoppinglist.errors;

public class EmailSendException extends Exception {
    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
