package no.eirikgg.shoppinglist.errors;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.http.HttpStatus;

public class PublicError {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private HttpStatus status;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Error error;

    public PublicError(HttpStatus status, Error error) {
        this.status = status;
        this.error = error;
    }

    public Error getError() {
        return error;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
