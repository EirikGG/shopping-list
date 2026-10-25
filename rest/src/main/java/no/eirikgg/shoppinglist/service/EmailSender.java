package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.errors.EmailSendException;

// Delivers an email. The app uses EmailSenderSmtp; tests swap in a fake.
public interface EmailSender {

    void sendEmail(EmailMessage emailMessage) throws EmailSendException;
}
