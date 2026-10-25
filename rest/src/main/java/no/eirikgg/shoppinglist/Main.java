package no.eirikgg.shoppinglist;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import no.eirikgg.shoppinglist.web.EmailWorker;
import no.eirikgg.shoppinglist.web.ExceptionHandlers;
import no.eirikgg.shoppinglist.web.controller.GeneralController;
import no.eirikgg.shoppinglist.web.controller.ItemController;
import no.eirikgg.shoppinglist.web.controller.StoreController;
import no.eirikgg.shoppinglist.web.filter.ClientIp;
import no.eirikgg.shoppinglist.web.filter.RateLimiter;
import no.eirikgg.shoppinglist.web.controller.UserController;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.PostgresDb;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;
import no.eirikgg.shoppinglist.web.filter.LogFilter;
import no.eirikgg.shoppinglist.service.EmailSender;
import no.eirikgg.shoppinglist.service.EmailSenderSmtp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting application...");
        Db db = PostgresDb.getMigratedInstance(
                System.getProperty("DB.HOSTNAME"),
                System.getProperty("DB.PORT"),
                System.getProperty("DB.NAME"),
                System.getProperty("DB.USERNAME"),
                System.getProperty("DB.PASSWORD"),
                System.getProperty("DB.POOL_SIZE")
        );
        String appPortProperty = System.getProperty("APP.PORT");
        int appPort = Integer.parseInt(appPortProperty);

        EmailSender emailSender = createEmailSender();

        log.info("Completed setup and migration");
        // Set in docker-compose.yml, where the backend is only reached through a proxy.
        boolean trustProxy = Boolean.parseBoolean(System.getenv("TRUST_PROXY"));
        log.info("Taking client addresses from {}", trustProxy ? ClientIp.HEADER : "the connection");
        Javalin app = setupJavalinApp(db, emailSender, trustProxy);

        log.info("Configured javalin routes");
        app.start(appPort);
    }


    // The mail settings come from environment variables rather than -D options:
    // JAVA_TOOL_OPTIONS is echoed to the log at startup, which would leak the
    // API key, and a sender like "Name <a@b.com>" has spaces.
    static EmailSender createEmailSender() {
        String host = System.getenv("MAIL_HOST");
        String port = System.getenv("MAIL_PORT");
        String from = System.getenv("MAIL_FROM");
        log.info("Sending email over SMTP through {}:{}", host, port);
        return new EmailSenderSmtp(
                host,
                port,
                System.getenv("MAIL_USERNAME"),
                System.getenv("MAIL_API_KEY"),
                null == from || from.isBlank() ? "Shopping list <noreply@example.com>" : from
        );
    }


    public static Javalin setupJavalinApp(Db db, EmailSender emailSender) {
        return setupJavalinApp(db, emailSender, false);
    }

    // trustProxy takes the client address from the proxy's X-Real-IP header.
    // See ClientIp for when that is safe.
    public static Javalin setupJavalinApp(Db db, EmailSender emailSender, boolean trustProxy) {
        Javalin app = Javalin.create(config -> {

            config.jsonMapper(new JavalinJackson(Json.MAPPER, false));
            ClientIp.setup(config, trustProxy);

            RateLimiter globalRateLimiter = new RateLimiter(50, Duration.ofMinutes(1));
            config.routes.before(globalRateLimiter::check);

            EmailWorker.setup(config, db, emailSender);

            LogFilter.setup(config);
            AuthenticationFilter.setup(config, db);

            GeneralController.setup(config, db);
            UserController.setup(config, db, emailSender);
            ItemController.setup(config, db);
            StoreController.setup(config, db);

            ExceptionHandlers.setup(config, db);

            config.events.serverStopped(db::close);
        });

        return app;
    }
}
