package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.datamodel.EmailTemplate;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.EmailRepository;
import no.eirikgg.shoppinglist.db.repository.UserRepository;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import no.eirikgg.shoppinglist.errors.StatusAlreadyChangedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class EmailService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;
    private EmailSenderSmtp emailSender;

    private UserRepository userRepository = new UserRepository();
    private EmailRepository emailRepository = new EmailRepository();


    public EmailService(Db db, EmailSenderSmtp emailSender) {
        this.db = db;
        this.emailSender = emailSender;
    }



    public void sendEmail(EmailMessage emailMessage) {
        try {
            emailSender.sendEmail(emailMessage);
        }
        catch (Exception e) {
            String errorMsg = "Failed to send email";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }


    public List<EmailMessage> getUnsendtEmailMessages() {
        try (Connection conn = db.getConnection()) {
            List<Integer> emailIds = emailRepository.getEmailIdsInStatus(conn,
                    EmailMessage.Status.CREATED
            );
            List<EmailMessage> emailMessages = new LinkedList<>();
            for (Integer emailId : emailIds) {
                EmailMessage emailMessage = emailRepository.getEmailMessage(conn, emailId).orElseThrow();
                emailMessages.add(emailMessage);
            }

            return emailMessages;
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void updateEmailMessageStatus(
            EmailMessage emailMessage, EmailMessage.Status from, EmailMessage.Status to
    ) throws StatusAlreadyChangedException {
        try (Connection conn = db.getConnection()) {
            try {
                conn.setAutoCommit(false);
                EmailMessage.Status oldStatus = emailRepository.readAndLockEmailStatus(conn, emailMessage);
                if (!from.equals(oldStatus)) {
                    throw new StatusAlreadyChangedException("Email message is not in status: %s (%s)".formatted(
                            from, oldStatus
                    ));
                }
                emailRepository.updateEmailMessage(conn, emailMessage, to);
                conn.commit();
            }
            catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void createUserVerificationEmail(UUID sessionId, User user) throws EmailNotRegisteredException {
        try (Connection conn = db.getConnection()) {
            int verificationCode = userRepository.getEmailVerificationCode(conn, user);

            String subject = EmailTemplate.NewUser.getSubject(
                    user.getPerson().getFirstName(),
                    user.getPerson().getLastName()
            );
            String body = EmailTemplate.NewUser.getBody(
                    verificationCode
            );

            emailRepository.createEmailMessage(
                conn,
                sessionId,
                user.getAuthorization().getEmail(),
                subject,
                body
            );
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
}