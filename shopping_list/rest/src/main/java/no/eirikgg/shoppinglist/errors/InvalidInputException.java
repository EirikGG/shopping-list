package no.eirikgg.shoppinglist.errors;

// The request asked for something not allowed, such as a link that is not
// http or https. The error tells the web app which message to show.
public class InvalidInputException extends Exception {

    private final Error error;

    public InvalidInputException(Error error, String msg) {
        super(msg);
        this.error = error;
    }

    public Error getError() {
        return error;
    }
}
