package no.eirikgg.shoppinglist.web.filter;

import io.javalin.Javalin;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import no.eirikgg.shoppinglist.service.JwtService;
import no.eirikgg.shoppinglist.web.controller.UserController;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Only a JWT signed with the app's secret, still valid, and naming an active
// session of the same person gets in.
class AuthenticationFilterTest {

    // From t0001 and t0003.
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_SESSION = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final EmbeddedPostgresDb db;
    private final Javalin app;
    private final TestConfig testConfig = new TestConfig(true, false);

    AuthenticationFilterTest() {
        db = EmbeddedPostgresDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-user-and-login.sql");
        migrator.migrate(db, "db/updates/t0003-create-second-test-user.sql");
        app = TestApp.create(db, new EmailSenderFake());
    }

    @Test
    void aValidToken_getsIn() {
        JavalinTest.test(app, testConfig, (server, client) ->
                assertEquals(200, status(client, TestApp.bearer(db, SESSION)))
        );
    }

    @Test
    void theSessionIdAlone_doesNotGetIn() {
        // What used to be the token, and what the request log shows.
        JavalinTest.test(app, testConfig, (server, client) -> {
            assertEquals(401, status(client, SESSION.toString()));
            assertEquals(401, status(client, "Bearer " + SESSION));
        });
    }

    @Test
    void aTokenSignedWithAnotherSecret_doesNotGetIn() {
        JwtService otherSecret = new JwtService("someone-elses-secret-that-is-also-long-enough");
        String forged = otherSecret.createToken(personId(), SESSION, Instant.now().plus(Duration.ofHours(1)));
        JavalinTest.test(app, testConfig, (server, client) ->
                assertEquals(401, status(client, "Bearer " + forged))
        );
    }

    @Test
    void anExpiredToken_doesNotGetIn() {
        String expired = TestApp.JWT.createToken(personId(), SESSION, Instant.now().minusSeconds(60));
        JavalinTest.test(app, testConfig, (server, client) ->
                assertEquals(401, status(client, "Bearer " + expired))
        );
    }

    @Test
    void aTokenNamingSomeoneElsesSession_doesNotGetIn() {
        // Signed properly, but the session belongs to the other test user.
        String mismatched = TestApp.JWT.createToken(personId(), OTHER_SESSION, Instant.now().plus(Duration.ofHours(1)));
        JavalinTest.test(app, testConfig, (server, client) ->
                assertEquals(401, status(client, "Bearer " + mismatched))
        );
    }

    @Test
    void noOrGarbledHeader_doesNotGetIn() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            assertEquals(401, status(client, null));
            assertEquals(401, status(client, "Bearer not.a.jwt"));
        });
    }

    // The person behind SESSION, read back from a token the helper signs.
    private int personId() {
        String bearer = TestApp.bearer(db, SESSION);
        return TestApp.JWT.verify(bearer.substring("Bearer ".length())).orElseThrow().personId();
    }

    private static int status(HttpClient client, String authorization) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(client.getOrigin() + UserController.PATH_SESSION_IS_VALID))
                .GET();
        if (null != authorization) {
            request.header("Authorization", authorization);
        }
        return java.net.http.HttpClient.newHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
