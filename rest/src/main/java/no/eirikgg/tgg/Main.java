package no.eirikgg.tgg;

import io.javalin.Javalin;
import no.eirikgg.tgg.controller.AuthenticationFilter;
import no.eirikgg.tgg.controller.EmailWorker;
import no.eirikgg.tgg.controller.ExceptionHandlers;
import no.eirikgg.tgg.controller.GeneralController;
import no.eirikgg.tgg.controller.LogFilter;
import no.eirikgg.tgg.controller.UserController;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.db.PostgresDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting application...");
        Db db = PostgresDb.getMigratedInstance(
                System.getProperty("DB.HOSTNAME"),
                System.getProperty("DB.PORT"),
                System.getProperty("DB.NAME"),
                System.getProperty("DB.USERNAME"),
                System.getProperty("DB.PASSWORD")
        );
        String appPortProperty = System.getProperty("APP.PORT");
        int appPort = Integer.parseInt(appPortProperty);

        log.info("Completed setup and migration");
        Javalin app = setupJavalinApp(db);

        log.info("Configured javalin routes");
        app.start(appPort);
    }


    public static Javalin setupJavalinApp(Db db) {
        String emailHost = System.getProperty("MAIL.HOST");
        String emailPort = System.getProperty("MAIL.PORT");

        Javalin app = Javalin.create(config -> {

            EmailWorker.setup(config, db, emailHost, emailPort);

            LogFilter.setup(config);
            AuthenticationFilter.setup(config, db);

            GeneralController.setup(config, db);
            UserController.setup(config, db, emailHost, emailPort);

            ExceptionHandlers.setup(config, db);
        });

        return app;
    }
}
