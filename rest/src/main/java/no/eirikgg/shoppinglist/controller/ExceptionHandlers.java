package no.eirikgg.shoppinglist.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.EmailAlreadyVerifiedException;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import no.eirikgg.shoppinglist.errors.PublicError;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;
import no.eirikgg.shoppinglist.errors.WrongEmailVerificationCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionHandlers {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private ExceptionHandlers() {}

    public static void setup(JavalinConfig config, Db db) {
        ExceptionHandlers eh = new ExceptionHandlers();
        config.routes.exception(UsernameAlreadyExistsException.class, eh::usernameAlreadyExistsException);
        config.routes.exception(EmailNotRegisteredException.class, eh::noEmailConnectedToVerificationUuidException);
        config.routes.exception(EmailAlreadyVerifiedException.class, eh::emailAlreadyVerifiedException);
        config.routes.exception(UserEmailNotVerifiedException.class, eh::userEmailNotVerifiedException);
        config.routes.exception(InvalidSessionException.class, eh::invalidSessionException);
        config.routes.exception(WrongEmailVerificationCodeException.class, eh::wrongEmailVerificationCodeException);
        config.routes.exception(Exception.class, eh::exception);
    }

    private void invalidSessionException(InvalidSessionException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.UNAUTHORIZED, Error.INVALID_SESSION_ID);
    }

    private void userEmailNotVerifiedException(UserEmailNotVerifiedException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.UNAUTHORIZED, Error.USER_EMAIL_NOT_VERIFIED);
    }


    private void usernameAlreadyExistsException(UsernameAlreadyExistsException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.BAD_REQUEST, Error.DUPLICATE_USERNAME);
    }


    private void noEmailConnectedToVerificationUuidException(EmailNotRegisteredException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.BAD_REQUEST, Error.NO_EMAIL_CONNECTED_TO_VERIFICATION_UUID);
    }


    private void emailAlreadyVerifiedException(EmailAlreadyVerifiedException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.BAD_REQUEST, Error.EMAIL_ALREADY_VERIFIED_EXCEPTION);
    }


    private void wrongEmailVerificationCodeException(WrongEmailVerificationCodeException e, Context ctx) {
        creatPublicError(ctx, e, HttpStatus.UNAUTHORIZED, Error.WRONG_EMAIL_VERIFICATION_CODE_EXCEPTION);
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
