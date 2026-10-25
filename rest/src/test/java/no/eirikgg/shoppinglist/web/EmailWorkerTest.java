package no.eirikgg.shoppinglist.web;

import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.repository.EmailRepository;
import no.eirikgg.shoppinglist.errors.EmailSendException;
import no.eirikgg.shoppinglist.service.EmailSender;
import no.eirikgg.shoppinglist.service.EmailService;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailWorkerTest {

    // From t0001.
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BROKEN = "broken@test.com";
    private static final String WORKING = "working@test.com";

    private final EmbeddedPostgresDb db = EmbeddedPostgresDb.createMigratedInstance();
    private final DbUtils dbUtils = new DbUtils();

    EmailWorkerTest() {
        new LiquibaseMigrator().migrate(db, "db/updates/t0001-create-test-user-and-login.sql");
    }

    @Test
    void aSendThatThrows_marksTheEmailFailed_andTheOthersAreStillSent() throws SQLException {
        // The failing one first, so it would have stopped the round before.
        createEmail(BROKEN);
        createEmail(WORKING);

        List<String> delivered = new ArrayList<>();
        EmailSender sender = message -> {
            if (BROKEN.equals(message.getAddress())) {
                throw new EmailSendException("Connect timed out");
            }
            delivered.add(message.getAddress());
        };

        new EmailWorker(new EmailService(db, sender)).run();

        assertEquals(EmailMessage.Status.FAILED, statusOf(BROKEN));
        assertEquals(EmailMessage.Status.SENDT, statusOf(WORKING));
        assertEquals(List.of(WORKING), delivered);
    }

    @Test
    void aFailedEmail_isNotTriedAgain() throws SQLException {
        createEmail(BROKEN);
        List<String> attempts = new ArrayList<>();
        EmailSender sender = message -> {
            attempts.add(message.getAddress());
            throw new EmailSendException("Connect timed out");
        };
        EmailWorker worker = new EmailWorker(new EmailService(db, sender));

        worker.run();
        worker.run();

        assertEquals(List.of(BROKEN), attempts);
        assertEquals(EmailMessage.Status.FAILED, statusOf(BROKEN));
    }

    private void createEmail(String address) throws SQLException {
        try (Connection conn = db.getConnection()) {
            new EmailRepository().createEmailMessage(conn, SESSION, address, "Subject", "<p>Body</p>");
        }
    }

    private EmailMessage.Status statusOf(String address) throws SQLException {
        try (Connection conn = db.getConnection()) {
            return dbUtils.selectSingle(conn,
                    "select status from email_message where address = ?",
                    address,
                    rs -> EmailMessage.Status.valueOf(rs.getString("status").orElseThrow())
            ).orElseThrow();
        }
    }
}
