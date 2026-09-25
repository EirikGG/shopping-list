package no.eirikgg.tgg.db.repository;

import no.eirikgg.tgg.datamodel.EmailMessage;
import no.eirikgg.tgg.db.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class EmailRepository {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;

    public EmailRepository(Db db){
        this.db = db;
    }


    public List<EmailMessage> getUnsendtEmailMessages() throws SQLException {
        try (Connection conn = db.getConnection()) {
            return db.select(conn, """
                select 
                    email_id,
                    session_id,
                    address,
                    status,
                    subject,
                    body,
                    created,
                    updated,
                    sent
                from 
                    email_message
                where 
                    sendt               is null
                    and status          like ?        
                """,
                EmailMessage.Status.CREATED,
                rs -> new EmailMessage(
                    rs.getInt("email_id").orElseThrow(),
                    UUID.fromString(rs.getString("session_id").orElseThrow()),
                    rs.getString("address").orElseThrow(),
                    EmailMessage.Status.valueOf(rs.getString("status").orElseThrow()),
                    rs.getString("subject").orElseThrow(),
                    rs.getString("body").orElseThrow(),
                    rs.getLocalDateTime("created").orElseThrow(),
                    rs.getLocalDateTime("updated").orElseThrow(),
                    rs.getLocalDateTime("sent").orElse(null)
                )
            );
        }
    }

    public void updateEmailMessage(EmailMessage emailMessage, EmailMessage.Status newStatus) throws SQLException {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);

            int numberOfAffectedRows = db.update(conn, """
                update email_message
                set status                  = ?
                where email_id              = ?
                """,
                newStatus,
                emailMessage.getId().orElseThrow()
            ).orElse(0);

            if (numberOfAffectedRows != 1) {
                throw new RuntimeException("Affected rows: " + numberOfAffectedRows);
            }

            conn.commit();
        }
        catch (SQLException sqlEx) {
            conn.rollback();
            throw sqlEx;
        }
        finally {
            if (!conn.isClosed()) {
                conn.close();
            }
        }
    }

    public void createEmailMessage(
            UUID session_id,
            String address,
            String subject,
            String body
    ) throws SQLException {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);
            db.insert(conn, """
                insert into email_message(
                    session_id,
                    address,
                    status,
                    subject,
                    body,
                    created,
                    updated
                )
                values (?,?,?,?,?,?,?)
                """,
                session_id,
                address,
                EmailMessage.Status.CREATED.toString(),
                subject,
                body,
                LocalDateTime.now(),
                LocalDateTime.now()
            );

            conn.commit();
        }
        catch (SQLException sqlEx) {
            conn.rollback();
            throw sqlEx;
        }
        finally {
            if (!conn.isClosed()) {
                conn.close();
            }
        }
    }
}
