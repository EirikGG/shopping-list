package no.eirikgg.tgg.controller;

import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.tgg.Utils;
import no.eirikgg.tgg.db.user.User;
import no.eirikgg.tgg.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;


public class AuthenticationFilter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private AuthenticationService authenticationService;

    public AuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void filter(Context ctx) {
        String authorizationHeader = ctx.header(Header.AUTHORIZATION);
        if  (null == authorizationHeader) {
            throw new UnauthorizedResponse("User not authorized");
        }

        UUID sessionId = UUID.fromString(authorizationHeader);
        Optional<User> user = authenticationService.getLoggedInUser(sessionId);

        if (user.isPresent()) {
            ctx.attribute(Utils.USER_PARAM, user.orElseThrow());
            ctx.attribute(Utils.SESSION_ID_PARAM, sessionId);
        }
        else {
            ctx.status(HttpStatus.UNAUTHORIZED);
            throw new UnauthorizedResponse("User not authorized");
        }
    }

}
