package no.eirikgg.shoppinglist.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.Header;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
import no.eirikgg.shoppinglist.service.AuthenticationService;
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

    public void filter(Context ctx) throws InvalidSessionException, UserEmailNotVerifiedException {
        String path = ctx.path();
        HandlerType method = ctx.method();

        if (HandlerType.OPTIONS.equals(method)) {
            return;
        }

        if (HandlerType.POST.equals(method) && (
                UserController.PATH_USER.equals(path) ||
                UserController.PATH_LOGIN.equals(path) ||
                UserController.PATH_VERIFY_EMAIL.equals(path)
        )) {
            log.debug("Skipping authentication filter on path [{}] - {}", method, path);
            return;
        }

        if (HandlerType.GET.equals(method) && (
                GeneralController.PATH_NOOP.equals(path) ||
                GeneralController.PATH_PING.equals(path)
        )) {
            log.debug("Skipping authentication filter on path [{}] - {}", method, path);
            return;
        }

        UUID sessionId = Optional.of(ctx)
                .map(context -> context.header(Header.AUTHORIZATION))
                .map(header -> header.replace("Bearer ", ""))
                .map(UUID::fromString)
                .orElseThrow(() -> new UnauthorizedResponse("User not authorized"));

        Optional<User> user = authenticationService.getLoggedInUser(sessionId);

        if (user.isPresent()) {
            log.debug("Logged in user: {}", user.get().getAuthorization().getEmail());
            ctx.attribute(PARAM_USER, user.orElseThrow());
            ctx.attribute(PARAM_SESSION_ID, sessionId);
        }
        else {
            log.debug("{} requires login - not authorized", path);
            throw new UnauthorizedResponse("User not authorized");
        }
    }

}
