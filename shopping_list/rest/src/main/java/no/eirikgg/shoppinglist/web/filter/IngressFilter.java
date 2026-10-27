package no.eirikgg.shoppinglist.web.filter;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HandlerType;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.service.PersonService;
import no.eirikgg.shoppinglist.web.controller.GeneralController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Home Assistant logs the user in. Its ingress proxy then forwards the request
// with the user's id and name in these headers.
//
// The headers are only trusted from the proxy's address: anyone else who could
// reach the port could set them and act as any user. A null trustedProxy
// trusts every address, which is only for local development and tests.
public class IngressFilter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String PARAM_PERSON = "person";

    public static final String HEADER_USER_ID = "X-Remote-User-Id";
    public static final String HEADER_USER_NAME = "X-Remote-User-Name";
    public static final String HEADER_USER_DISPLAY_NAME = "X-Remote-User-Display-Name";

    // The address HA's ingress proxy connects from, inside HA's own network.
    public static final String HA_INGRESS_PROXY = "172.30.32.2";

    private final PersonService personService;
    private final String trustedProxy;

    public IngressFilter(PersonService personService, String trustedProxy) {
        this.personService = personService;
        this.trustedProxy = trustedProxy;
    }

    public static void setup(JavalinConfig config, Db db, String trustedProxy) {
        IngressFilter filter = new IngressFilter(new PersonService(db), trustedProxy);
        config.routes.beforeMatched(filter::filter);
    }

    public void filter(Context ctx) {
        if (skip(ctx.method(), ctx.path())) {
            return;
        }

        String remoteAddress = ctx.req().getRemoteAddr();
        if (null != trustedProxy && !trustedProxy.equals(remoteAddress)) {
            log.warn("Refusing request from {}, which is not the ingress proxy", remoteAddress);
            throw new ForbiddenResponse("Only reachable through Home Assistant");
        }

        String haUserId = ctx.header(HEADER_USER_ID);
        if (null == haUserId || haUserId.isBlank()) {
            throw new UnauthorizedResponse("No Home Assistant user");
        }

        Person person = personService.getOrCreate(haUserId.trim(), name(ctx));
        ctx.attribute(PARAM_PERSON, person);
    }

    // The display name, or the username for users without one.
    private static String name(Context ctx) {
        String displayName = ctx.header(HEADER_USER_DISPLAY_NAME);
        if (null != displayName && !displayName.isBlank()) {
            return displayName.trim();
        }
        String userName = ctx.header(HEADER_USER_NAME);
        return null == userName || userName.isBlank() ? null : userName.trim();
    }

    private boolean skip(HandlerType method, String path) {
        if (HandlerType.OPTIONS.equals(method)) {
            return true;
        }

        // Health checks, which come from HA's supervisor rather than a user.
        return HandlerType.GET.equals(method) && (
                GeneralController.PATH_NOOP.equals(path) ||
                GeneralController.PATH_PING.equals(path)
        );
    }
}
