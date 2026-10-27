package no.eirikgg.shoppinglist;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import no.eirikgg.shoppinglist.web.ExceptionHandlers;
import no.eirikgg.shoppinglist.web.WebApp;
import no.eirikgg.shoppinglist.web.controller.AttachmentController;
import no.eirikgg.shoppinglist.web.controller.ItemLinkController;
import no.eirikgg.shoppinglist.service.AttachmentService;
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
    private static final String DEFAULT_FILES_DIR = "/data/files";
    // The port HA's ingress proxy connects to unless the add-on says otherwise.
    private static final String DEFAULT_APP_PORT = "8099";

    public static void main(String[] args) {
        log.info("Starting application...");
        Path dbFile = Path.of(System.getProperty("DB.FILE", DEFAULT_DB_FILE));
        log.info("Using the database in {}", dbFile.toAbsolutePath());
        Db db = SqliteDb.openMigrated(dbFile);
        int appPort = Integer.parseInt(System.getProperty("APP.PORT", DEFAULT_APP_PORT));
        // The built web app. Not set in development, where Vite serves it.
        String webDirProperty = System.getProperty("WEB.DIR");
        Path webDir = null == webDirProperty || webDirProperty.isBlank() ? null : Path.of(webDirProperty);
        log.info(null == webDir ? "Serving only the API" : "Serving the web app from {}", webDir);
        Path filesDir = Path.of(System.getProperty("FILES.DIR", DEFAULT_FILES_DIR));
        log.info("Keeping uploaded files in {}", filesDir.toAbsolutePath());
        new AttachmentService(db, filesDir).removeUnusedFiles();

        log.info("Completed setup and migration");
        String trustedProxy = trustedProxy(System.getenv("INGRESS_PROXY"));
        if (null == trustedProxy) {
            log.warn("Trusting Home Assistant user headers from any address. Never do this in production.");
        }
        else {
            log.info("Trusting Home Assistant user headers from {}", trustedProxy);
        }
        Javalin app = setupJavalinApp(db, trustedProxy, webDir, filesDir);

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
    // accepted from. null accepts them from anyone; see IngressFilter. The API
    // is under /api; webDir, if set, is the built web app, served from /.
    // filesDir is where uploaded files are kept.
    public static Javalin setupJavalinApp(Db db, String trustedProxy, Path webDir, Path filesDir) {
        Javalin app = Javalin.create(config -> {

            config.jsonMapper(new JavalinJackson(Json.MAPPER, false));

            if (null != webDir) {
                WebApp.setup(config, webDir);
            }

            RequestLog.setup(config);
            IngressFilter.setup(config, db, trustedProxy);

            GeneralController.setup(config, db);
            ItemController.setup(config, db);
            StoreController.setup(config, db);
            ItemLinkController.setup(config, db);
            AttachmentController.setup(config, db, filesDir);

            ExceptionHandlers.setup(config, db);

            config.events.serverStopped(db::close);
        });

        return app;
    }
}
