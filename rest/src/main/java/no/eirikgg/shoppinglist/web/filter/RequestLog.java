package no.eirikgg.shoppinglist.web.filter;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import no.eirikgg.shoppinglist.datamodel.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// One line per request, written once the response is sent, so it has the
// status and the time taken. For logged-in requests it has the session id and
// the user's email.
//
// The Authorization header is never logged: it holds the login token, and
// anyone with it could act as the user. The session id is safe to log, as it
// is useless without the token's signature.
public class RequestLog {
    private static final Logger log = LoggerFactory.getLogger(RequestLog.class);

    private RequestLog() {}

    public static void setup(JavalinConfig config) {
        config.requestLogger.http(RequestLog::log);
    }

    private static void log(Context ctx, Float executionTimeMs) {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        long ms = Math.round(executionTimeMs);

        if (null == session) {
            log.info("[{}] {} {} {} ms", ctx.method(), ctx.path(), ctx.statusCode(), ms);
        }
        else {
            log.info("[{}] {} {} {} ms - session {} - user {}",
                    ctx.method(), ctx.path(), ctx.statusCode(), ms,
                    session.getSessionId(),
                    session.getUser().getAuthorization().getEmail()
            );
        }
    }
}
