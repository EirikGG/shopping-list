package no.eirikgg.shoppinglist.web;

import io.javalin.config.JavalinConfig;
import no.eirikgg.shoppinglist.datamodel.EmailMessage;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.StatusAlreadyChangedException;
import no.eirikgg.shoppinglist.service.EmailService;
import no.eirikgg.shoppinglist.service.EmailSender;
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

    // Package-private so tests can run a round directly.
    EmailWorker(EmailService emailService) {
        this.emailService = emailService;
    }

    public static void setup(JavalinConfig config, Db db, EmailSender emailSender) {
        EmailService emailService = new EmailService(db, emailSender);
        EmailWorker emailWorker = new EmailWorker(emailService);

        config.events.serverStarted(() -> {
            ScheduledExecutorService ex = emailWorker.executor;
            ex.scheduleWithFixedDelay(emailWorker::run, 10, 10, TimeUnit.SECONDS);
        });

        config.events.serverStopped(emailWorker.executor::shutdown);

        log.info("Completed email worker setup");
    }




    // One round: every email waiting is tried once. A failed email does not
    // stop the others.
    void run() {
        List<EmailMessage> emailMessages;
        try {
            emailMessages = emailService.getUnsendtEmailMessages();
        }
        catch (Exception ex) {
            log.error("Could not read unsent emails", ex);
            return;
        }

        log.debug("Found {} unsent email messages", emailMessages.size());
        for (EmailMessage emailMessage : emailMessages) {
            send(emailMessage);
        }
    }

    // CREATED -> PROCESSING -> SENDT, or FAILED if sending throws.
    private void send(EmailMessage emailMessage) {
        int emailId = emailMessage.getId().orElseThrow();
        try {
            emailService.updateEmailMessageStatus(
                    emailMessage,
                    EmailMessage.Status.CREATED,
                    EmailMessage.Status.PROCESSING
            );
        }
        catch (StatusAlreadyChangedException e) {
            log.info("Email {} was already picked up, skipping it", emailId);
            return;
        }
        catch (Exception e) {
            log.error("Could not start sending email {}", emailId, e);
            return;
        }

        log.info("Sending mail with mail id {}", emailId);
        try {
            emailService.sendEmail(emailMessage);
        }
        catch (Exception e) {
            // EmailService has already logged the full cause.
            log.error("Email {} failed, marking it {}", emailId, EmailMessage.Status.FAILED);
            setStatus(emailMessage, EmailMessage.Status.FAILED);
            return;
        }
        setStatus(emailMessage, EmailMessage.Status.SENDT);
    }

    private void setStatus(EmailMessage emailMessage, EmailMessage.Status status) {
        try {
            emailService.updateEmailMessageStatus(emailMessage, EmailMessage.Status.PROCESSING, status);
        }
        catch (Exception e) {
            log.error("Could not mark email {} as {}", emailMessage.getId().orElseThrow(), status, e);
        }
    }

}
