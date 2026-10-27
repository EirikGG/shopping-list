package no.eirikgg.shoppinglist;

import io.javalin.Javalin;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;

import java.net.http.HttpRequest;

// Builds the app for tests, and requests from the Home Assistant users the
// ingress proxy would pass on.
public final class TestApp {

    public static final String USER_ID = "test-ha-user";
    public static final String USER_NAME = "Test User";
    public static final String SECOND_USER_ID = "second-test-ha-user";
    public static final String SECOND_USER_NAME = "Second User";

    private TestApp() {}

    // Trusts the user headers from any address, as the tests are not behind
    // Home Assistant.
    public static Javalin create(Db db) {
        return create(db, null);
    }

    public static Javalin create(Db db, String trustedProxy) {
        return Main.setupJavalinApp(db, trustedProxy);
    }

    // A request from the test user.
    public static HttpRequest.Builder asUser(HttpRequest.Builder builder) {
        return asUser(builder, USER_ID, USER_NAME);
    }

    public static HttpRequest.Builder asUser(HttpRequest.Builder builder, String haUserId, String displayName) {
        return builder
                .header(IngressFilter.HEADER_USER_ID, haUserId)
                .header(IngressFilter.HEADER_USER_DISPLAY_NAME, displayName);
    }
}
