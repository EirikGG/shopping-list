package no.eirikgg.shoppinglist.web.filter;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.Header;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.web.controller.GeneralController;
import no.eirikgg.shoppinglist.web.controller.UserController;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
import no.eirikgg.shoppinglist.service.AuthenticationService;
import no.eirikgg.shoppinglist.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class AuthenticationFilter {
    private final Logger log = LoggerFactory.getLogger(getClass());


    public static final String PARAM_SESSION = "session";
    public static final String PARAM_SESSION_ID = "session";


    private static final String BEARER = "Bearer ";

    private AuthenticationService authenticationService;
    private JwtService jwtService;

    public AuthenticationFilter(AuthenticationService authenticationService, JwtService jwtService) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    public static void setup(JavalinConfig config, Db db, JwtService jwtService) {
        AuthenticationService authenticationService = new AuthenticationService(db);
        AuthenticationFilter authenticationFilter = new AuthenticationFilter(authenticationService, jwtService);
        config.routes.beforeMatched(authenticationFilter::filter);
    }

    public void filter(Context ctx) throws InvalidSessionException, UserEmailNotVerifiedException {
        String path = ctx.path();
        HandlerType method = ctx.method();

        if (skipLogin(method, path)) {
            return;
        }

        // The token is a JWT from the login. Its signature and expiry are
        // checked first; then the session it names must still be active in the
        // database, so logging out ends it, and it must belong to the same person.
        String header = ctx.header(Header.AUTHORIZATION);
        if (null == header || header.isBlank()) {
            throw new UnauthorizedResponse("User not authorized");
        }
        String token = header.startsWith(BEARER) ? header.substring(BEARER.length()) : header;

        JwtService.Claims claims = jwtService.verify(token.trim()).orElseThrow(
                () -> new UnauthorizedResponse("User not authorized")
        );

        User user = authenticationService.getLoggedInUser(claims.sessionId()).orElseThrow(
                () -> new UnauthorizedResponse("User not authorized")
        );
        if (user.getPerson().getId().orElseThrow() != claims.personId()) {
            throw new UnauthorizedResponse("User not authorized");
        }

        // Read by the request log (RequestLog), which never logs the token.
        Session session = new Session(claims.sessionId(), user);
        ctx.attribute(PARAM_SESSION, session);
    }

    private boolean skipLogin(HandlerType method, String path) {
        if (HandlerType.OPTIONS.equals(method)) {
            return true;
        }

        if (HandlerType.POST.equals(method) && (
                UserController.PATH_USER.equals(path) ||
                UserController.PATH_LOGIN.equals(path) ||
                UserController.PATH_VERIFY_EMAIL.equals(path)
        )) {
            log.debug("Skipping authentication filter on path [{}] - {}", method, path);
            return true;
        }

        if (HandlerType.GET.equals(method) && (
                GeneralController.PATH_NOOP.equals(path) ||
                GeneralController.PATH_PING.equals(path)
        )) {
            log.debug("Skipping authentication filter on path [{}] - {}", method, path);
            return true;
        }

        return false;
    }

}
