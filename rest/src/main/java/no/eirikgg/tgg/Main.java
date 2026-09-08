package no.eirikgg.tgg;

import io.javalin.Javalin;
import no.eirikgg.tgg.controller.AuthenticationFilter;
import no.eirikgg.tgg.controller.GeneralController;
import no.eirikgg.tgg.controller.LogFilter;
import no.eirikgg.tgg.controller.UserController;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.db.PostgresDb;
import no.eirikgg.tgg.service.AuthenticationService;
import no.eirikgg.tgg.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting application...");

        Db db = PostgresDb.createMigratedInstance(
                System.getProperty("DB.HOSTNAME"),
                System.getProperty("DB.PORT"),
                System.getProperty("DB.NAME"),
                System.getProperty("DB.USERNAME"),
                System.getProperty("DB.PASSWORD")
        );

        log.info("Completed setup and migration");

        LogFilter logFilter = new LogFilter();
        AuthenticationFilter authenticationFilter = new AuthenticationFilter(
                new AuthenticationService(db)
        );

        GeneralController generalController = new GeneralController(db);
        UserController userController = new UserController(
                new UserService(db)
        );

        Javalin app = Javalin.create(config -> {
            config.routes.beforeMatched(logFilter::filter);
            config.routes.beforeMatched(authenticationFilter::filter);

            config.routes.get("/ping", generalController::pingDb);
            config.routes.get("/noop", generalController::noop);

            config.routes.post("/user", userController::createUser);
        });

        log.info("Configured javalin routes");

        String appPortProperty = System.getProperty("APP.PORT");
        int appPort = Integer.parseInt(appPortProperty);
        app.start(appPort);
    }
}
