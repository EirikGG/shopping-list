package no.eirikgg.tgg.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.tgg.datamodel.User;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;


public class AuthenticationFilter {
    private final Logger log = LoggerFactory.getLogger(getClass());


    public static final String PARAM_USER = "user";
    public static final String PARAM_SESSION_ID = "session-id";


    private AuthenticationService authenticationService;

    public AuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public static void setup(JavalinConfig config, Db db) {
        AuthenticationService authenticationService = new AuthenticationService(db);
        AuthenticationFilter authenticationFilter = new AuthenticationFilter(authenticationService);
        config.routes.beforeMatched(authenticationFilter::filter);
    }

    public void filter(Context ctx) {
        String path = ctx.path();
        HandlerType method = ctx.method();

        if (HandlerType.OPTIONS.equals(method)) {
            return;
        }

        if (HandlerType.POST.equals(method) && (
                UserController.PATH_USER.equals(path) ||
                UserController.PATH_LOGIN.equals(path)
        )) {
            log.debug("Skipping authentication filter on path [{}] - {}", method, path);
            return;
        }

        UUID sessionId = AuthenticationService.getSessionIdFromRequest(ctx).orElseThrow(
                () -> new UnauthorizedResponse("User not authorized")
        );
        Optional<User> user = authenticationService.getLoggedInUser(sessionId);

        if (user.isPresent()) {
            log.debug("Logged in user: {}", user.get().getAuthorization().getUsername());
            ctx.attribute(PARAM_USER, user.orElseThrow());
            ctx.attribute(PARAM_SESSION_ID, sessionId);
        }
        else {
            log.debug("{} requires login - not authorized", path);
            throw new UnauthorizedResponse("User not authorized");
        }
    }

}
