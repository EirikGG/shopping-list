package no.eirikgg.shoppinglist;

import io.javalin.Javalin;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.service.EmailSender;
import no.eirikgg.shoppinglist.service.JwtService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// Builds the app for tests, and login tokens for the sessions the test data
// creates (see the t000x SQL files).
public final class TestApp {

    public static final JwtService JWT = new JwtService("test-only-secret-that-is-long-enough-for-hs256");

    private TestApp() {}

    public static Javalin create(Db db, EmailSender emailSender) {
        return create(db, emailSender, false);
    }

    public static Javalin create(Db db, EmailSender emailSender, boolean trustProxy) {
        return Main.setupJavalinApp(db, emailSender, JWT, trustProxy);
    }

    // The Authorization header value for an existing session.
    public static String bearer(Db db, UUID sessionId) {
        try (Connection conn = db.getConnection()) {
            int personId = new DbUtils().selectSingle(conn, """
                    select login.person_id
                    from session as s
                    join person_login as login
                        on login.person_login_id = s.person_login_id
                    where s.session_id = ?
                    """,
                    sessionId,
                    rs -> rs.getInt("person_id").orElseThrow()
            ).orElseThrow(() -> new IllegalStateException("No test session " + sessionId));

            return "Bearer " + JWT.createToken(personId, sessionId, Instant.now().plus(Duration.ofHours(1)));
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
