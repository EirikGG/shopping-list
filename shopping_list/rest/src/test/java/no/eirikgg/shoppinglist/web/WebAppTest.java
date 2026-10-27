package no.eirikgg.shoppinglist.web;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.db.TestDb;
import no.eirikgg.shoppinglist.web.controller.ItemController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebAppTest {

    private final TestDb db = TestDb.createMigratedInstance();
    private final TestConfig testConfig = new TestConfig(true, false);

    @TempDir
    Path webDir;

    @AfterEach
    void closeDb() {
        db.close();
    }

    private Javalin app() throws IOException {
        Files.writeString(webDir.resolve("index.html"), "<html>the app</html>");
        Files.createDirectories(webDir.resolve("assets"));
        Files.writeString(webDir.resolve("assets").resolve("index-abc123.js"), "console.log('app')");
        return Main.setupJavalinApp(db, null, webDir);
    }

    @Test
    void theAppIsServedNextToTheApi() throws IOException {
        JavalinTest.test(app(), testConfig, (server, client) -> {
            HttpResponse<String> page = get(client, "/");
            assertEquals(200, page.statusCode());
            assertEquals("<html>the app</html>", page.body());
            assertEquals("no-cache", page.headers().firstValue("Cache-Control").orElse(null));

            HttpResponse<String> api = get(client, ItemController.PATH_ITEM_LIST);
            assertEquals(200, api.statusCode());
            assertEquals("[]", api.body());
        });
    }

    @Test
    void assets_areCachedForGood() throws IOException {
        JavalinTest.test(app(), testConfig, (server, client) -> {
            HttpResponse<String> asset = get(client, "/assets/index-abc123.js");
            assertEquals(200, asset.statusCode());
            assertEquals(
                    "public, max-age=31536000, immutable",
                    asset.headers().firstValue("Cache-Control").orElse(null)
            );
        });
    }

    private static HttpResponse<String> get(io.javalin.testtools.HttpClient client, String path)
            throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
