package no.eirikgg.shoppinglist.web.controller;

import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemControllerTest {

    private EmbeddedPostgresDb db;
    private EmailSenderFake fakeEmailSender;
    private Javalin app;
    private TestConfig testConfig;

    private final String LIST_NAME = "unit-test-list";
    private final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public ItemControllerTest() {
        db = EmbeddedPostgresDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-user-and-login.sql");

        fakeEmailSender = new EmailSenderFake();
        testConfig = new TestConfig(true, false);
        app = Main.setupJavalinApp(db, fakeEmailSender);
    }

    @Test
    void createItemList_andDeleteIt() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            HttpRequest request = HttpRequest.newBuilder()
                 .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
                 .header(Header.AUTHORIZATION, SESSION_ID.toString())
                 .POST(HttpRequest.BodyPublishers.ofString(
                         "{\"%s\": \"%s\"}".formatted(ItemController.PARAM_ITEM_LIST_NAME, LIST_NAME)
                 ))
                 .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                  .send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
        });
    }


}