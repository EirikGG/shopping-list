package no.eirikgg.shoppinglist.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.staticfiles.Location;

import java.nio.file.Path;
import java.util.Map;

// Serves the built web app (web/dist) next to the API, so the add-on is one
// container. Every link in it is relative, as Home Assistant's ingress serves
// the add-on under /api/hassio_ingress/<token>/.
public class WebApp {

    private WebApp() {}

    public static void setup(JavalinConfig config, Path directory) {
        String root = directory.toAbsolutePath().toString();

        // Vite puts a content hash in these file names, so they never change.
        config.staticFiles.add(staticFiles -> {
            staticFiles.hostedPath = "/assets";
            staticFiles.directory = directory.resolve("assets").toAbsolutePath().toString();
            staticFiles.location = Location.EXTERNAL;
            staticFiles.headers = Map.of("Cache-Control", "public, max-age=31536000, immutable");
        });

        // index.html must be fetched fresh, so an update shows up straight away.
        config.staticFiles.add(staticFiles -> {
            staticFiles.hostedPath = "/";
            staticFiles.directory = root;
            staticFiles.location = Location.EXTERNAL;
            staticFiles.headers = Map.of("Cache-Control", "no-cache");
        });
    }
}
