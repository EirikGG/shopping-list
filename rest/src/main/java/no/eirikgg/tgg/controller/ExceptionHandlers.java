package no.eirikgg.tgg.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.errors.Error;
import no.eirikgg.tgg.errors.PublicError;
import no.eirikgg.tgg.errors.UsernameAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionHandlers {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private ExceptionHandlers() {}

    public static void setup(JavalinConfig config, Db db) {
        ExceptionHandlers eh = new ExceptionHandlers();
        config.routes.exception(UsernameAlreadyExistsException.class, eh::usernameAlreadyExistsException);
        config.routes.exception(Exception.class, eh::exception);
    }

    private void logException(Exception ex) {
        log.error(ex.getMessage(), ex);
    }

    private void usernameAlreadyExistsException(UsernameAlreadyExistsException e, Context ctx) {
        logException(e);
        HttpStatus errorStatus = HttpStatus.BAD_REQUEST;
        PublicError publicError = new PublicError(errorStatus, Error.DUPLICATE_USERNAME);
        ctx.status(errorStatus).json(publicError);
    }

    private void exception(Exception e, Context ctx) {
        logException(e);
        throw new InternalServerErrorResponse();
    }
}
