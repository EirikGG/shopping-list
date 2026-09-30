package no.eirikgg.shoppinglist.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.EmailAlreadyVerifiedException;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.errors.NoEmailConnectedToVerificationUuidException;
import no.eirikgg.shoppinglist.errors.PublicError;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionHandlers {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private ExceptionHandlers() {}

    public static void setup(JavalinConfig config, Db db) {
        ExceptionHandlers eh = new ExceptionHandlers();
        config.routes.exception(UsernameAlreadyExistsException.class, eh::usernameAlreadyExistsException);
        config.routes.exception(NoEmailConnectedToVerificationUuidException.class, eh::noEmailConnectedToVerificationUuidException);
        config.routes.exception(EmailAlreadyVerifiedException.class, eh::emailAlreadyVerifiedException);
        config.routes.exception(Exception.class, eh::exception);
    }


    private void usernameAlreadyExistsException(UsernameAlreadyExistsException e, Context ctx) {
        HttpStatus errorStatus = HttpStatus.BAD_REQUEST;
        PublicError publicError = new PublicError(errorStatus, Error.DUPLICATE_USERNAME);
        ctx.status(errorStatus).json(publicError);
    }


    private void noEmailConnectedToVerificationUuidException(NoEmailConnectedToVerificationUuidException e, Context ctx) {
        HttpStatus errorStatus = HttpStatus.BAD_REQUEST;
        PublicError publicError = new PublicError(errorStatus, Error.NO_EMAIL_CONNECTED_TO_VERIFICATION_UUID);
        ctx.status(errorStatus).json(publicError);
    }


    private void emailAlreadyVerifiedException(EmailAlreadyVerifiedException e, Context ctx) {
        HttpStatus errorStatus = HttpStatus.BAD_REQUEST;
        PublicError publicError = new PublicError(errorStatus, Error.EMAIL_ALREADY_VERIFIED_EXCEPTION);
        ctx.status(errorStatus).json(publicError);
    }


    private void exception(Exception e, Context ctx) {
        log.error(e.getMessage(), e);
        throw new InternalServerErrorResponse();
    }
}
