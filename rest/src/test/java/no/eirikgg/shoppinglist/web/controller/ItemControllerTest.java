package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private EmbeddedPostgresDb db;
    private EmailSenderFake fakeEmailSender;
    private Javalin app;
    private TestConfig testConfig;

    private final String ITEM_LIST_NAME = "unit-test-list";
    private final int ITEM_LIST_ID;
    private final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ObjectMapper mapper = Json.MAPPER;

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
            String itemName = "test-item1";
            ItemController.PostItemResponse item = createItem(client, itemName);
            ItemController.GetItemResponse getResponse = getItem(client, item.id());
            assertEquals(itemName, getResponse.name());

            String itemName2 = "test-item2";
            ItemController.PatchItemResponse patchResponse = patchItem(client, item.id(), itemName2, true);
            assertEquals(item.id(), patchResponse.id());
            assertEquals(itemName2, patchResponse.name());
            assertNotNull(patchResponse.completed());

            deleteItem(client, item.id());
            assertNull(getItem(client, item.id()));
        });
    }

    @Test
    void createItem_withStores() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int storeAId = createStore(client, "item-test-store-a");
            int storeBId = createStore(client, "item-test-store-b");

            ItemController.PostItemResponse item = createItem(
                    client, "test-item-with-stores", List.of(storeBId, storeAId)
            );
            assertEquals(List.of(storeAId, storeBId), storeIds(item.stores()));
            assertEquals("item-test-store-a", item.stores().getFirst().name());

            ItemController.GetItemResponse getResponse = getItem(client, item.id());
            assertEquals(List.of(storeAId, storeBId), storeIds(getResponse.stores()));

            ItemController.PostItemResponse itemWithoutStore = createItem(client, "test-item-without-store");
            assertTrue(itemWithoutStore.stores().isEmpty());
        });
    }

    @Test
    void addAndRemoveStoresOfItem() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int firstStoreId = createStore(client, "first-store");
            int secondStoreId = createStore(client, "second-store");
            ItemController.PostItemResponse item = createItem(
                    client, "item-changing-store", List.of(firstStoreId)
            );

            sendMapping(client, "POST", new StoreController.MapStoreAndItemRequest(secondStoreId, item.id()));
            // Adding a store the item already has changes nothing.
            sendMapping(client, "POST", new StoreController.MapStoreAndItemRequest(secondStoreId, item.id()));
            assertEquals(List.of(firstStoreId, secondStoreId), storeIds(getItem(client, item.id()).stores()));

            sendMapping(client, "DELETE", new StoreController.UnmapStoreAndItemRequest(firstStoreId, item.id()));
            assertEquals(List.of(secondStoreId), storeIds(getItem(client, item.id()).stores()));
        });
    }

    private static List<Integer> storeIds(List<ItemController.ItemStoreResponse> stores) {
        return stores.stream().map(ItemController.ItemStoreResponse::storeId).toList();
    }

    private void sendMapping(HttpClient client, String method, Object requestBody)
            throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + StoreController.PATH_STORE_ITEM_MAPPING))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .method(method, getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private int createStore(HttpClient client, String storeName) throws IOException, InterruptedException {
        StoreController.PostStoreRequest requestBody = new StoreController.PostStoreRequest(storeName);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + StoreController.PATH_STORE))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .POST(getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(201, response.statusCode());
        return mapper.readValue(response.body(), StoreController.PostStoreResponse.class).storeId();
    }

    private void deleteItem(
            HttpClient client, int itemId
    ) throws IOException, InterruptedException {
        ItemController.DeleteItemRequest requestBody = new ItemController.DeleteItemRequest(itemId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .method("DELETE", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private ItemController.PatchItemResponse patchItem(HttpClient client, int itemId, String newName, boolean completed)
            throws IOException, InterruptedException
    {
        ItemController.PatchItemRequest requestBody = new ItemController.PatchItemRequest(itemId, newName, completed);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .method("PATCH", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        ItemController.PatchItemResponse patchItemRequest = mapper.readValue(
                response.body(), ItemController.PatchItemResponse.class
        );

        assertEquals(200, response.statusCode());
        log.info("{}", patchItemRequest);
        return patchItemRequest;
    }

    private ItemController.PostItemResponse createItem(HttpClient client, String itemName)
            throws IOException, InterruptedException
    {
        return createItem(client, itemName, null);
    }

    private ItemController.PostItemResponse createItem(HttpClient client, String itemName, List<Integer> storeIds)
            throws IOException, InterruptedException
    {
        ItemController.PostItemRequest requestBody = new ItemController.PostItemRequest(
                ITEM_LIST_ID, itemName, storeIds
        );
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

    private ItemController.GetItemResponse getItem(HttpClient client, int itemId)
            throws IOException, InterruptedException
    {
        ItemController.GetItemRequest requestBody = new ItemController.GetItemRequest(itemId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .method("GET", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        if (null == response || null == response.body() || response.body().isBlank()) {
            assertEquals(404, response.statusCode());
            return null;
        }

        ItemController.GetItemResponse getItemResponse = mapper.readValue(
                response.body(), ItemController.GetItemResponse.class
        );

        assertEquals(200, response.statusCode());
        log.info("{}", getItemResponse);
        return getItemResponse;
    }

    private HttpRequest.BodyPublisher getBodyPublisher(Object requestObject) throws JsonProcessingException {
        String requestBody = mapper.writeValueAsString(requestObject);
        return HttpRequest.BodyPublishers.ofString(requestBody);
    }
}