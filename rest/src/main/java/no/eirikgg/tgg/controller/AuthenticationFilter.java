package no.eirikgg.tgg.controller;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.Header;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.tgg.Main;
import no.eirikgg.tgg.datamodel.User;
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
        String path = ctx.path();
        HandlerType method = ctx.method();

        if (HandlerType.OPTIONS.equals(method)) {
            return;
        }

        if (HandlerType.POST.equals(method) && (
                UserController.USER_PATH.equals(path) ||
                UserController.LOGIN_PATH.equals(path)
        )) {
            log.debug("Skipping authentication filter on path [{}] - {}", method, path);
            return;
        }

        String authorizationHeader = ctx.header(Header.AUTHORIZATION);
        if  (null == authorizationHeader) {
            log.debug("{} called with no authorized header", path);
            throw new UnauthorizedResponse("User not authorized");
        }

        authorizationHeader = authorizationHeader.replace("Bearer ", "");
        UUID sessionId = UUID.fromString(authorizationHeader);
        Optional<User> user = authenticationService.getLoggedInUser(sessionId);

        if (user.isPresent()) {
            log.debug("Logged in user: {}", user.get().getAuthorization().getUsername());
            ctx.attribute(Main.USER_PARAM, user.orElseThrow());
            ctx.attribute(Main.SESSION_ID_PARAM, sessionId);
        }
        else {
            log.debug("{} requires login - not authorized", path);
            throw new UnauthorizedResponse("User not authorized");
        }
    }

}
