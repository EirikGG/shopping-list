package no.eirikgg.shoppinglist.web.controller;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class ItemControllerTest {

    private EmbeddedPostgresDb db;
    private EmailSenderFake fakeEmailSender;
    private Javalin app;
    private TestConfig testConfig;

    private final String LIST_NAME = "";

    public ItemControllerTest() {
        db = EmbeddedPostgresDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-user-and-login.sql");

        fakeEmailSender = new EmailSenderFake();
        testConfig = new TestConfig(true, false);
        app = Main.setupJavalinApp(db, fakeEmailSender);
    }

    @Test
    @Order(1)
    void createItemList_andDeleteIt() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            HttpRequest request = HttpRequest.newBuilder()
                 .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
                 .header("Authorization", "")
                 .POST(HttpRequest.BodyPublishers.noBody())
                 .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                  .send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
        });
    }


}