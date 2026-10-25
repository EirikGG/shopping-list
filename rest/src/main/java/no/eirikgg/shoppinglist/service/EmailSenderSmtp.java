package no.eirikgg.shoppinglist.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.errors.EmailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Properties;

// Sends over SMTP. Which server is only a matter of settings: Mailpit in dev
// takes mail without login, while Resend in prod (smtp.resend.com, username
// "resend", the API key as password) needs both login and TLS.
public class EmailSenderSmtp implements EmailSender {

    private final Logger log = LoggerFactory.getLogger(getClass());

    // Port for TLS from the first byte. Any other port with a login upgrades
    // to TLS with STARTTLS.
    static final int IMPLICIT_TLS_PORT = 465;

    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final String from;


    // username and password may be null for a server without login.
    public EmailSenderSmtp(String smtpHost, String smtpPort, String username, String password, String from) {
        this.smtpHost = Objects.requireNonNull(smtpHost, "SMTP host must not be null");
        this.smtpPort = Integer.parseInt(Objects.requireNonNull(smtpPort, "SMTP port must not be null"));
        this.username = blankToNull(username);
        this.password = blankToNull(password);
        this.from = Objects.requireNonNull(from, "Sender address must not be null");

        if ((null == this.username) != (null == this.password)) {
            throw new IllegalArgumentException("SMTP username and password must be set together");
        }
    }


    @Override
    public void sendEmail(EmailMessage emailMessage) throws EmailSendException {
        try {
            send(emailMessage);
        }
        catch (MessagingException e) {
            throw new EmailSendException("Could not send email over SMTP", e);
        }
    }

    private void send(EmailMessage emailMessage) throws MessagingException {
        log.info("Sending email...");

        Session session = Session.getInstance(smtpProperties());

        MimeMessage message = new MimeMessage(session);

        message.setFrom(new InternetAddress(from));
        message.setRecipients(
                Message.RecipientType.TO,
                InternetAddress.parse(emailMessage.getAddress())
        );

        message.setSubject(emailMessage.getSubject());
        message.setContent(emailMessage.getBody(), "text/html; charset=UTF-8");

        if (null == username) {
            Transport.send(message);
        }
        else {
            Transport.send(message, username, password);
        }
    }

    // A password is never sent unencrypted: with a login, TLS is required.
    Properties smtpProperties() {
        boolean login = null != username;
        boolean implicitTls = login && IMPLICIT_TLS_PORT == smtpPort;

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", String.valueOf(login));
        props.put("mail.smtp.ssl.enable", String.valueOf(implicitTls));
        props.put("mail.smtp.starttls.enable", String.valueOf(login && !implicitTls));
        props.put("mail.smtp.starttls.required", String.valueOf(login && !implicitTls));
        if (login) {
            props.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");
        return props;
    }

    private static String blankToNull(String value) {
        return null == value || value.isBlank() ? null : value.trim();
    }
}
