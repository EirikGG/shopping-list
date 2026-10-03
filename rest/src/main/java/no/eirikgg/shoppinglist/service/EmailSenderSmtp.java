package no.eirikgg.shoppinglist.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Properties;

public class EmailSenderSmtp {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private String smtpHost;
    private int smtpPort;


    public EmailSenderSmtp(String smtpHost, String smtpPort) {
        Objects.requireNonNull(smtpHost, "SMTP host must not be null");
        Objects.requireNonNull(smtpPort, "SMTP port must not be null");

        this.smtpHost = smtpHost;
        this.smtpPort = Integer.parseInt(smtpPort);
    }


    public void sendEmail(EmailMessage emailMessage) throws MessagingException {
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
}
