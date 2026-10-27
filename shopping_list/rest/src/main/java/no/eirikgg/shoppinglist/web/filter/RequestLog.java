package no.eirikgg.shoppinglist.web.filter;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import no.eirikgg.shoppinglist.datamodel.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// One line per request, written once the response is sent, so it has the
// status and the time taken. For a Home Assistant user's request it has the
// user's name and HA id.
public class RequestLog {
    private static final Logger log = LoggerFactory.getLogger(RequestLog.class);

    private RequestLog() {}

    public static void setup(JavalinConfig config) {
        config.requestLogger.http(RequestLog::log);
    }

    private static void log(Context ctx, Float executionTimeMs) {
        Person person = ctx.attribute(IngressFilter.PARAM_PERSON);
        long ms = Math.round(executionTimeMs);

        if (null == person) {
            log.info("[{}] {} {} {} ms", ctx.method(), ctx.path(), ctx.statusCode(), ms);
        }
        else {
            log.info("[{}] {} {} {} ms - user {} ({})",
                    ctx.method(), ctx.path(), ctx.statusCode(), ms,
                    person.name(),
                    person.haUserId()
            );
        }
    }
}
