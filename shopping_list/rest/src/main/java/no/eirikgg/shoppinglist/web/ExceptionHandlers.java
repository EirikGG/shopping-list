package no.eirikgg.shoppinglist.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.PublicError;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionHandlers {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private ExceptionHandlers() {}

    public static void setup(JavalinConfig config, Db db) {
        ExceptionHandlers eh = new ExceptionHandlers();
        config.routes.exception(NoAccessToResourceException.class, eh::noAccessToResourceException);
        config.routes.exception(ResourceNotFoundException.class, eh::resourceNotFoundException);
        config.routes.exception(Exception.class, eh::exception);
    }


    private void noAccessToResourceException(NoAccessToResourceException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.FORBIDDEN, Error.NO_ACCESS);
    }

    private void resourceNotFoundException(ResourceNotFoundException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.NOT_FOUND, Error.RESOURCE_NOT_FOUND);
    }





    private void exception(Exception e, Context ctx) {
        log.error(e.getMessage(), e);
        throw new InternalServerErrorResponse();
    }



    private void creatPublicError(Context ctx, Exception e, HttpStatus httpStatus, Error error) {
        log.debug("Returning public error with http status {} and error {}", httpStatus, error);
        PublicError publicError = new PublicError(httpStatus, error);
        ctx.status(httpStatus).json(publicError);
    }
}
