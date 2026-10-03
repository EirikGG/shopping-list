package no.eirikgg.shoppinglist.db.user;

import jakarta.mail.MessagingException;
import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.service.EmailSenderSmtp;

public class EmailSenderFake extends EmailSenderSmtp {

    private EmailMessage emailMessage;

    public EmailSenderFake() {
        super("unit-test", "1234");
    }


    @Override
    public void sendEmail(EmailMessage emailMessage) throws MessagingException {
        this.emailMessage = emailMessage;
    }

    public EmailMessage getEmailMessage() {
        return emailMessage;
    }
}
