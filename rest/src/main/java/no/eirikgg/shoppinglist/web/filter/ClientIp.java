package no.eirikgg.shoppinglist.web.filter;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

// Works out which address a request comes from, for ctx.ip().
//
// All browser traffic reaches the backend through a proxy: nginx in prod and
// test, the Vite dev server in dev. The connection then comes from the proxy,
// so every user would share one address and one rate limit. The proxy passes
// the real address in X-Real-IP, overwriting anything the client sent.
//
// The header is only trusted with trustProxy, and only because the backend is
// not reachable except through the proxy (docker-compose.yml publishes its
// port on localhost only). Anyone who can reach the backend directly could
// otherwise pick their own address and dodge the limits.
public final class ClientIp {

    public static final String HEADER = "X-Real-IP";

    private ClientIp() {}

    public static void setup(JavalinConfig config, boolean trustProxy) {
        if (trustProxy) {
            config.contextResolver.ip = ClientIp::fromProxy;
        }
    }

    private static String fromProxy(Context ctx) {
        String forwarded = ctx.header(HEADER);
        if (null == forwarded || forwarded.isBlank()) {
            return ctx.req().getRemoteAddr();
        }
        return forwarded.trim();
    }
}
