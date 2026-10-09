package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private EmbeddedPostgresDb db;
    private EmailSenderFake fakeEmailSender;
    private Javalin app;
    private TestConfig testConfig;

    private final String LIST_NAME = "unit-test-list";
    private final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ObjectMapper mapper = new ObjectMapper();

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
            for (int i = 0; i < 5; i++) {
                createItemList(client);

                List<ItemController.GetItemListResponse> itemLists = getItemList(client);
                assertEquals(1, itemLists.size());

                int itemListId = itemLists.get(0).id();
                deleteItemList(client, itemListId);
            }
        });
    }

    private void deleteItemList(io.javalin.testtools.HttpClient client, int listId)
            throws IOException, InterruptedException
    {

        String url = client.getOrigin() + ItemController.PATH_ITEM_LIST;
        url += "?%s=%d".formatted(ItemController.PARAM_ITEM_LIST_ID, listId);
        HttpRequest request = HttpRequest.newBuilder()
             .uri(URI.create(url))
             .header(Header.AUTHORIZATION, SESSION_ID.toString())
             .DELETE()
             .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    private ItemController.SaveItemListResponse createItemList(io.javalin.testtools.HttpClient client)
            throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .POST(HttpRequest.BodyPublishers.ofString(
                 "{\"%s\": \"%s\"}".formatted(ItemController.PARAM_ITEM_LIST_NAME, LIST_NAME)
            ))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        ItemController.SaveItemListResponse saveItemListResponse = mapper.readValue(
                response.body(),
                ItemController.SaveItemListResponse.class
        );

        assertEquals(200, response.statusCode());
        log.info("{}", saveItemListResponse);
        return saveItemListResponse;
    }


    private List<ItemController.GetItemListResponse> getItemList(io.javalin.testtools.HttpClient client)
            throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .GET()
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        List<ItemController.GetItemListResponse> mappedResponse = mapper.readValue(
                response.body(),
                mapper.getTypeFactory().constructCollectionType(List.class, ItemController.GetItemListResponse.class)
        );
        assertEquals(200, response.statusCode());

        return mappedResponse;
    }


}