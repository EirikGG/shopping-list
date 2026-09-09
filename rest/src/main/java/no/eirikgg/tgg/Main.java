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

        Db db = PostgresDb.getMigratedInstance(
                System.getProperty("DB.HOSTNAME"),
                System.getProperty("DB.PORT"),
                System.getProperty("DB.NAME"),
                System.getProperty("DB.USERNAME"),
                System.getProperty("DB.PASSWORD")
        );
        log.info("Completed setup and migration");

        Javalin app = setupJavalinApp(db);
        log.info("Configured javalin routes");

        String appPortProperty = System.getProperty("APP.PORT");
        int appPort = Integer.parseInt(appPortProperty);
        app.start(appPort);
    }


    public static Javalin setupJavalinApp(Db db) {
        AuthenticationService authenticationService = new AuthenticationService(db);
        UserService userService = new UserService(db);

        LogFilter logFilter = new LogFilter();
        AuthenticationFilter authenticationFilter = new AuthenticationFilter(authenticationService);

        GeneralController generalController = new GeneralController(db);
        UserController userController = new UserController(userService, authenticationService);

        Javalin app = Javalin.create(config -> {
            config.routes.beforeMatched(logFilter::filter);
            config.routes.beforeMatched(authenticationFilter::filter);

            config.routes.get("/ping", generalController::pingDb);
            config.routes.get("/noop", generalController::noop);

            config.routes.post(Utils.PATH_USER, userController::createUser);
            config.routes.post(Utils.PATH_LOGIN, userController::login);
        });

        return app;
    }
}
