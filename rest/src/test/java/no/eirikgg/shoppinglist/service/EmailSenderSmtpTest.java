package no.eirikgg.shoppinglist.service;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailSenderSmtpTest {

    private static final String FROM = "Shopping list <noreply@test.com>";

    @Test
    void withoutLogin_likeMailpit_noAuthAndNoTls() {
        Properties props = new EmailSenderSmtp("mailpit", "1025", null, "", FROM).smtpProperties();

        assertEquals("false", props.get("mail.smtp.auth"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"));
        assertEquals("false", props.get("mail.smtp.starttls.enable"));
    }

    @Test
    void withLoginOnPort465_likeResend_usesTlsFromTheStart() {
        Properties props = new EmailSenderSmtp("smtp.resend.com", "465", "resend", "re_key", FROM).smtpProperties();

        assertEquals("true", props.get("mail.smtp.auth"));
        assertEquals("true", props.get("mail.smtp.ssl.enable"));
        assertEquals("false", props.get("mail.smtp.starttls.enable"));
    }

    @Test
    void withLoginOnAnotherPort_requiresStartTls() {
        Properties props = new EmailSenderSmtp("smtp.resend.com", "587", "resend", "re_key", FROM).smtpProperties();

        assertEquals("true", props.get("mail.smtp.auth"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"));
        // Required, so the password is never sent if the server does not offer TLS.
        assertEquals("true", props.get("mail.smtp.starttls.required"));
    }

    @Test
    void usernameAndPassword_mustBeSetTogether() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailSenderSmtp("smtp.resend.com", "465", "resend", null, FROM));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailSenderSmtp("smtp.resend.com", "465", "", "re_key", FROM));
    }
}
