package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.datamodel.EmailTemplate;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.EmailRepository;
import no.eirikgg.shoppinglist.db.repository.UserRepository;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
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
            return emailRepository.getUnsendtEmailMessages(conn);
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void updateEmailMessageStatus(EmailMessage emailMessage, EmailMessage.Status status) {
        try (Connection conn = db.getConnection()) {
            emailRepository.updateEmailMessage(conn, emailMessage, status);
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