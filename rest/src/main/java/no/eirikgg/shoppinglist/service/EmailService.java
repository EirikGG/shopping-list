package no.eirikgg.shoppinglist.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.datamodel.EmailTemplate;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class EmailService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private EmailRepository emailRepository;

    private String smtpHost;
    private String smtpPort;


    public EmailService(Db db, String smtpHost, String smtpPort) {
        this.emailRepository = new EmailRepository(db);
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
    }



    public void sendEmail(EmailMessage emailMessage) {
        try {
            log.info("Sending email...");

            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "false");

            Session session = Session.getInstance(props);

            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress("noreply@example.com"));
            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(emailMessage.getAddress())
            );

            message.setSubject(emailMessage.getSubject());
            message.setContent(emailMessage.getBody(), "text/html; charset=UTF-8");

            Transport.send(message);
        }
        catch (Exception e) {
            String errorMsg = "Failed to send email";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }


    public List<EmailMessage> getUnsendtEmailMessages() {
        try {
            return emailRepository.getUnsendtEmailMessages();
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void updateEmailMessageStatus(EmailMessage emailMessage, EmailMessage.Status status) {
        try {
            emailRepository.updateEmailMessage(emailMessage, status);
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void createUserVerificationEmail(UUID sessionId, User user, String verificationUrl) {
        try {
            emailRepository.createEmailMessage(
                sessionId,
                user.getEmail().getEmail(),
                EmailTemplate.NewUser.getSubject(
                        user.getPerson().getFirstName(),
                        user.getPerson().getLastName()
                ),
                EmailTemplate.NewUser.getBody(verificationUrl)
            );
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
}