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

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class EmailService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private UserRepository userRepository;

    private EmailRepository emailRepository;
    private EmailSenderSmtp emailSender;

    public EmailService(Db db, EmailSenderSmtp emailSender) {
        this.userRepository = new UserRepository(db);
        this.emailRepository = new EmailRepository(db);
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

    public void createUserVerificationEmail(UUID sessionId, User user) throws EmailNotRegisteredException {
        try {

            int verificationNum = userRepository.getEmailVerificationCode(user);

            emailRepository.createEmailMessage(
                sessionId,
                user.getAuthorization().getEmail(),
                EmailTemplate.NewUser.getSubject(
                        user.getPerson().getFirstName(),
                        user.getPerson().getLastName()
                ),
                EmailTemplate.NewUser.getBody(
                        verificationNum
                )
            );
        }
        catch (SQLException e) {
            String errorMsg = "Failed to get unsent emails";
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
}