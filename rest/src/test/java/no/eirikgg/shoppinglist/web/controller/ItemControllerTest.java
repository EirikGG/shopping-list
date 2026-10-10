package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private EmbeddedPostgresDb db;
    private EmailSenderFake fakeEmailSender;
    private Javalin app;
    private TestConfig testConfig;

    private final String ITEM_LIST_NAME = "unit-test-list";
    private final int ITEM_LIST_ID;
    private final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ObjectMapper mapper = new ObjectMapper();

    public ItemControllerTest() throws SQLException {
        db = EmbeddedPostgresDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-user-and-login.sql");
        migrator.migrate(db, "db/updates/t0002-create-item-list-for-test-user.sql");

        fakeEmailSender = new EmailSenderFake();
        testConfig = new TestConfig(true, false);
        app = Main.setupJavalinApp(db, fakeEmailSender);
        DbUtils dbUtils = new DbUtils();
        try (Connection conn = db.getConnection()) {
            ITEM_LIST_ID = dbUtils.selectSingle(conn,
                    "select item_list_id from item_list where name = ?",
                    ITEM_LIST_NAME,
                    rs -> rs.getInt("item_list_id").orElseThrow()
            ).orElseThrow();
        }
    }

    @Test
    void createItems_andDeleteIt() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            ItemController.PostItemResponse item1 = createItem(client, "test-item1");
            ItemController.PostItemResponse item2 = createItem(client, "test-item2");
            ItemController.PostItemResponse item3 = createItem(client, "test-item3");
            ItemController.PostItemResponse item4 = createItem(client, "test-item4");





//                List<ItemController.GetItemListResponse> items = getItem(client);
//                assertEquals(1, items.size());
//                int itemId = items.get(0).id();
//
//                String newName = UUID.randomUUID().toString();
//                updateItem(client, itemId, newName);
//
//                for (ItemController.GetItemListResponse itemList : getItem(client)) {
//                    assertEquals(newName, itemList.name());
//                }
//
//                deleteItem(client, itemId);
        });
    }

    private ItemController.PostItemResponse createItem(HttpClient client, String itemName)
            throws IOException, InterruptedException
    {
        ItemController.PostItemRequest requestBody = new ItemController.PostItemRequest(ITEM_LIST_ID, itemName);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .POST(getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        ItemController.PostItemResponse postItemResponse = mapper.readValue(
                response.body(), ItemController.PostItemResponse.class
        );

        assertEquals(201, response.statusCode());
        log.info("{}", postItemResponse);
        return postItemResponse;
    }

    private HttpRequest.BodyPublisher getBodyPublisher(Object requestObject) throws JsonProcessingException {
        String requestBody = mapper.writeValueAsString(requestObject);
        return HttpRequest.BodyPublishers.ofString(requestBody);
    }
}