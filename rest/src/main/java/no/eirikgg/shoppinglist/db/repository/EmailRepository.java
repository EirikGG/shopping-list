package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EmailRepository {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private DbUtils dbUtils = new DbUtils();


    public Optional<EmailMessage> getEmailMessage(Connection conn, int emailId) {
        return dbUtils.selectSingle(conn, """
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
                email_id = ?
            """,
            emailId,
            rs -> new EmailMessage(
                rs.getInt("email_id").orElseThrow(),
                rs.getUuid("session_id").orElseThrow(),
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

    public EmailMessage.Status readAndLockEmailStatus(Connection conn, EmailMessage emailMessage) {
        return dbUtils.selectSingle(conn,
                "select status from email_message where email_id = ? for update",
                emailMessage.getId().orElseThrow(),
                rs -> EmailMessage.Status.valueOf(rs.getString("status").orElseThrow())
        ).orElseThrow();
    }

    public void updateEmailMessage(
            Connection conn,
            EmailMessage emailMessage,
            EmailMessage.Status newStatus
    ) throws SQLException {
        dbUtils.update(conn, """
            update email_message
            set status                  = ?
            where email_id              = ?
            """,
            newStatus.name(),
            emailMessage.getId().orElseThrow()
        ).orElse(0);
    }

    public void createEmailMessage(
            Connection conn,
            UUID sessionId,
            String address,
            String subject,
            String body
    ) throws SQLException {
        dbUtils.insert(conn, """
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
            sessionId,
            address,
            EmailMessage.Status.CREATED.name(),
            subject,
            Hidden.param(body),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    public List<Integer> getEmailIdsInStatus(Connection conn, EmailMessage.Status status) {
        return dbUtils.select(conn,
                "select email_id from email_message where status = ?",
                status.name(),
                rs -> rs.getInt("email_id").orElseThrow()
        );
    }
}
