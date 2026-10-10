package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Header;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import org.junit.jupiter.api.Test;
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

class ItemListControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private EmbeddedPostgresDb db;
    private EmailSenderFake fakeEmailSender;
    private Javalin app;
    private TestConfig testConfig;

    private final String LIST_NAME = "unit-test-list";
    private final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ObjectMapper mapper = new ObjectMapper();

    public ItemListControllerTest() {
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

                String newName = UUID.randomUUID().toString();
                updateItemList(client, itemListId, newName);

                for (ItemController.GetItemListResponse itemList : getItemList(client)) {
                    assertEquals(newName, itemList.name());
                }

                deleteItemList(client, itemListId);
            }
        });
    }


    private void updateItemList(io.javalin.testtools.HttpClient client, int itemListId, String newName)
            throws IOException, InterruptedException
    {
        ItemController.PatchItemListRequest requestBody = new ItemController.PatchItemListRequest(
                itemListId, newName
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
                .header(Header.AUTHORIZATION, SESSION_ID.toString())
                .header(Header.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .method("PATCH", getBodyPublisher(requestBody))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    private void deleteItemList(io.javalin.testtools.HttpClient client, int itemListId)
            throws IOException, InterruptedException
    {
        ItemController.DeleteItemListRequest requestBody = new ItemController.DeleteItemListRequest(
                itemListId
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
                .header(Header.AUTHORIZATION, SESSION_ID.toString())
                .method("DELETE", getBodyPublisher(requestBody))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    private ItemController.PostItemListResponse createItemList(io.javalin.testtools.HttpClient client)
            throws IOException, InterruptedException
    {
        ItemController.PostItemListRequest requestBody = new ItemController.PostItemListRequest(LIST_NAME);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .POST(getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        ItemController.PostItemListResponse saveItemListResponse = mapper.readValue(
                response.body(),
                ItemController.PostItemListResponse.class
        );

        assertEquals(201, response.statusCode());
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

    private HttpRequest.BodyPublisher getBodyPublisher(Object requestObject) throws JsonProcessingException {
        String requestBody = mapper.writeValueAsString(requestObject);
        return HttpRequest.BodyPublishers.ofString(requestBody);
    }
}