package no.eirikgg.tgg;

import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.HttpStatus;
import no.eirikgg.tgg.controller.AuthenticationFilter;
import no.eirikgg.tgg.controller.GeneralController;
import no.eirikgg.tgg.controller.LogFilter;
import no.eirikgg.tgg.controller.UserController;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.db.PostgresDb;
import no.eirikgg.tgg.errors.Error;
import no.eirikgg.tgg.errors.PublicError;
import no.eirikgg.tgg.errors.UsernameAlreadyExistsException;
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
            RoutesConfig routes = config.routes;
            routes.beforeMatched(logFilter::filter);
            routes.beforeMatched(authenticationFilter::filter);

            routes.get(GeneralController.PING_PATH, generalController::pingDb);
            routes.get(GeneralController.NOOP_PATH, generalController::noop);

            routes.post(UserController.PATH_USER, userController::createUser);
            routes.post(UserController.PATH_LOGIN, userController::login);
            routes.get(UserController.PATH_SESSION_IS_VALID, userController::isValid);


            routes.exception(UsernameAlreadyExistsException.class, (ex, ctx) -> {
                HttpStatus errorStatus = HttpStatus.BAD_REQUEST;
                PublicError publicError = new PublicError(errorStatus, Error.DUPLICATE_USERNAME);
                ctx.status(errorStatus).json(publicError);
            });
        });



        return app;
    }
}
