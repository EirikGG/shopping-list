package no.eirikgg.shoppinglist;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import no.eirikgg.shoppinglist.web.ExceptionHandlers;
import no.eirikgg.shoppinglist.web.controller.GeneralController;
import no.eirikgg.shoppinglist.web.controller.ItemController;
import no.eirikgg.shoppinglist.web.controller.StoreController;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.SqliteDb;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;
import no.eirikgg.shoppinglist.web.filter.RequestLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // INGRESS_PROXY set to this trusts the user headers from any address. Only
    // for local development, where there is no Home Assistant in front.
    private static final String TRUST_ANY_PROXY = "any";

    // The add-on's /data folder, which HA keeps across updates and backs up.
    private static final String DEFAULT_DB_FILE = "/data/shoppinglist.db";

    public static void main(String[] args) {
        log.info("Starting application...");
        Path dbFile = Path.of(System.getProperty("DB.FILE", DEFAULT_DB_FILE));
        log.info("Using the database in {}", dbFile.toAbsolutePath());
        Db db = SqliteDb.openMigrated(dbFile);
        String appPortProperty = System.getProperty("APP.PORT");
        int appPort = Integer.parseInt(appPortProperty);

        log.info("Completed setup and migration");
        String trustedProxy = trustedProxy(System.getenv("INGRESS_PROXY"));
        if (null == trustedProxy) {
            log.warn("Trusting Home Assistant user headers from any address. Never do this in production.");
        }
        else {
            log.info("Trusting Home Assistant user headers from {}", trustedProxy);
        }
        Javalin app = setupJavalinApp(db, trustedProxy);

        log.info("Configured javalin routes");
        app.start(appPort);
    }

    // HA's ingress proxy unless set; null for "any".
    static String trustedProxy(String setting) {
        if (null == setting || setting.isBlank()) {
            return IngressFilter.HA_INGRESS_PROXY;
        }
        return TRUST_ANY_PROXY.equalsIgnoreCase(setting.trim()) ? null : setting.trim();
    }


    // trustedProxy is the only address the Home Assistant user headers are
    // accepted from. null accepts them from anyone; see IngressFilter.
    public static Javalin setupJavalinApp(Db db, String trustedProxy) {
        Javalin app = Javalin.create(config -> {

            config.jsonMapper(new JavalinJackson(Json.MAPPER, false));

            RequestLog.setup(config);
            IngressFilter.setup(config, db, trustedProxy);

            GeneralController.setup(config, db);
            ItemController.setup(config, db);
            StoreController.setup(config, db);

            ExceptionHandlers.setup(config, db);

            config.events.serverStopped(db::close);
        });

        return app;
    }
}
