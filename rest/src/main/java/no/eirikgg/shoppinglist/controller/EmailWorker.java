package no.eirikgg.shoppinglist.controller;

import io.javalin.config.JavalinConfig;
import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.service.EmailService;
import no.eirikgg.shoppinglist.service.EmailSenderSmtp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmailWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailWorker.class);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();



    private EmailService emailService;

    private EmailWorker(EmailService emailService) {
        this.emailService = emailService;
    }

    public static void setup(JavalinConfig config, Db db, EmailSenderSmtp emailSender) {
        EmailService emailService = new EmailService(db, emailSender);
        EmailWorker emailWorker = new EmailWorker(emailService);

        config.events.serverStarted(() -> {
            ScheduledExecutorService ex = emailWorker.executor;
            ex.scheduleWithFixedDelay(emailWorker::run, 10, 10, TimeUnit.SECONDS);
        });

        config.events.serverStopped(emailWorker.executor::shutdown);

        log.info("Completed email worker setup");
    }




    private void run() {
        try {
            List<EmailMessage> emailMessages = emailService.getUnsendtEmailMessages();
            log.debug("Found {} unsent email messages", emailMessages.size());
            for (EmailMessage emailMessage : emailMessages) {
                log.info("Sending mail with mail id {}",  emailMessage.getId().orElseThrow());
                emailService.updateEmailMessageStatus(emailMessage, EmailMessage.Status.PROCESSING);
                emailService.sendEmail(emailMessage);
                emailService.updateEmailMessageStatus(emailMessage, EmailMessage.Status.SENDT);
            }
        }
        catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

}
