package no.eirikgg.shoppinglist.web.filter;

import io.javalin.Javalin;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// The global limit is 50 requests a minute per address and path.
class ClientIpTest {

    private static final int LIMIT = 50;
    private static final String PATH = "/rate-limit-check";
    private static final String USER_A = "203.0.113.1";
    private static final String USER_B = "203.0.113.2";

    private final EmbeddedPostgresDb db = EmbeddedPostgresDb.createMigratedInstance();
    private final TestConfig testConfig = new TestConfig(true, false);

    @Test
    void behindTheProxy_eachUserHasTheirOwnLimit() {
        Javalin app = TestApp.create(db, new EmailSenderFake(), true);
        JavalinTest.test(app, testConfig, (server, client) -> {
            for (int i = 0; i < LIMIT; i++) {
                assertNotEquals(429, get(client, USER_A));
            }
            assertEquals(429, get(client, USER_A));

            // Another user behind the same proxy is not affected.
            assertNotEquals(429, get(client, USER_B));
        });
    }

    @Test
    void withoutTrustingTheProxy_theHeaderCannotDodgeTheLimit() {
        Javalin app = TestApp.create(db, new EmailSenderFake(), false);
        JavalinTest.test(app, testConfig, (server, client) -> {
            for (int i = 0; i < LIMIT; i++) {
                get(client, USER_A);
            }
            assertEquals(429, get(client, USER_A));

            // A made-up X-Real-IP is ignored, so this is still the same client.
            assertEquals(429, get(client, USER_B));
        });
    }

    private static int get(HttpClient client, String realIp) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(client.getOrigin() + PATH))
                .header(ClientIp.HEADER, realIp)
                .GET()
                .build();
        return java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
