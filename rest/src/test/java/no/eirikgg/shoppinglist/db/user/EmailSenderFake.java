package no.eirikgg.shoppinglist.db.user;

import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.service.EmailSender;

public class EmailSenderFake implements EmailSender {

    private EmailMessage emailMessage;


    @Override
    public void sendEmail(EmailMessage emailMessage) {
        this.emailMessage = emailMessage;
    }

    public EmailMessage getEmailMessage() {
        return emailMessage;
    }
}
