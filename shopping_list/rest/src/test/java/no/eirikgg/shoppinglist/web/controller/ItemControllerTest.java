package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.TestDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private TestDb db;
    private Javalin app;
    private TestConfig testConfig;

    private final String ITEM_LIST_NAME = "unit-test-list";
    private final int ITEM_LIST_ID;
    private final ObjectMapper mapper = Json.MAPPER;

    public ItemControllerTest() throws SQLException {
        db = TestDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-list.sql");

        testConfig = new TestConfig(true, false);
        app = TestApp.create(db);
        DbUtils dbUtils = new DbUtils();
        try (Connection conn = db.getConnection()) {
            ITEM_LIST_ID = dbUtils.selectSingle(conn,
                    "select item_list_id from item_list where name = ?",
                    ITEM_LIST_NAME,
                    rs -> rs.getInt("item_list_id").orElseThrow()
            ).orElseThrow();
        }
    }

    @AfterEach
    void closeDb() {
        db.close();
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

    // Open items newest added first, then completed items last completed first.
    @Test
    void items_areOrderedNewestFirst() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int first = createItem(client, "first").id();
            Thread.sleep(5);
            createItem(client, "second");
            Thread.sleep(5);
            int third = createItem(client, "third").id();
            Thread.sleep(5);
            createItem(client, "fourth");

            // Completed in a different order than they were added.
            patchItem(client, third, "third", true);
            Thread.sleep(5);
            patchItem(client, first, "first", true);

            assertEquals(List.of("fourth", "second", "first", "third"), itemNamesInOrder(client));
        });
    }

    // When the item was added, which renaming or completing it does not change.
    @Test
    void created_isSetWhenAdded_andKept() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            ItemController.PostItemResponse item = createItem(client, "milk");
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            assertNotNull(item.created());
            assertTrue(item.created().isAfter(before) && item.created().isBefore(after));
            assertEquals(item.created(), getItem(client, item.id()).created());

            ItemController.PatchItemResponse patched = patchItem(client, item.id(), "oat milk", true);
            assertEquals(item.created(), patched.created());
            assertEquals(item.created(), getItem(client, item.id()).created());
        });
    }

    // A new item has 1. Leaving the amount out of a change keeps it.
    @Test
    void amount_isOneWhenAdded_andCanBeChanged() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            ItemController.PostItemResponse item = createItem(client, "eggs");
            assertEquals(1, item.amount());

            assertEquals(12, patchItem(client, item.id(), "eggs", 12, false).amount());
            assertEquals(12, getItem(client, item.id()).amount());

            ItemController.PatchItemResponse completed = patchItem(client, item.id(), "eggs", true);
            assertEquals(12, completed.amount());
        });
    }

    @Test
    void amount_outsideOneTo999_isRefused() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            ItemController.PostItemResponse item = createItem(client, "butter");

            for (int amount : new int[] {0, -1, 1000}) {
                HttpResponse<String> response = sendPatch(
                        client, new ItemController.PatchItemRequest(item.id(), "butter", amount, false)
                );
                assertEquals(400, response.statusCode());
                assertTrue(response.body().contains("INVALID_AMOUNT"));
            }
            assertEquals(1, getItem(client, item.id()).amount());
        });
    }

    @Test
    void createItem_withLabels() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int labelAId = createLabel(client, "item-test-label-a");
            int labelBId = createLabel(client, "item-test-label-b");

            ItemController.PostItemResponse item = createItem(
                    client, "test-item-with-labels", List.of(labelBId, labelAId)
            );
            assertEquals(List.of(labelAId, labelBId), labelIds(item.labels()));
            assertEquals("item-test-label-a", item.labels().getFirst().name());

            ItemController.GetItemResponse getResponse = getItem(client, item.id());
            assertEquals(List.of(labelAId, labelBId), labelIds(getResponse.labels()));

            ItemController.PostItemResponse itemWithoutLabel = createItem(client, "test-item-without-label");
            assertTrue(itemWithoutLabel.labels().isEmpty());
        });
    }

    @Test
    void addAndRemoveLabelsOfItem() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int firstLabelId = createLabel(client, "first-label");
            int secondLabelId = createLabel(client, "second-label");
            ItemController.PostItemResponse item = createItem(
                    client, "item-changing-label", List.of(firstLabelId)
            );

            sendMapping(client, "POST", new LabelController.MapLabelAndItemRequest(secondLabelId, item.id()));
            // Adding a label the item already has changes nothing.
            sendMapping(client, "POST", new LabelController.MapLabelAndItemRequest(secondLabelId, item.id()));
            assertEquals(List.of(firstLabelId, secondLabelId), labelIds(getItem(client, item.id()).labels()));

            sendMapping(client, "DELETE", new LabelController.UnmapLabelAndItemRequest(firstLabelId, item.id()));
            assertEquals(List.of(secondLabelId), labelIds(getItem(client, item.id()).labels()));
        });
    }

    private static List<Integer> labelIds(List<ItemController.ItemLabelResponse> labels) {
        return labels.stream().map(ItemController.ItemLabelResponse::labelId).toList();
    }

    private void sendMapping(HttpClient client, String method, Object requestBody)
            throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + LabelController.PATH_LABEL_ITEM_MAPPING))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .method(method, getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private int createLabel(HttpClient client, String labelName) throws IOException, InterruptedException {
        LabelController.PostLabelRequest requestBody = new LabelController.PostLabelRequest(ITEM_LIST_ID, labelName);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + LabelController.PATH_LABEL))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .POST(getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(201, response.statusCode());
        return mapper.readValue(response.body(), LabelController.PostLabelResponse.class).labelId();
    }

    private void deleteItem(
            HttpClient client, int itemId
    ) throws IOException, InterruptedException {
        ItemController.DeleteItemRequest requestBody = new ItemController.DeleteItemRequest(itemId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
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
        return patchItem(client, itemId, newName, null, completed);
    }

    private ItemController.PatchItemResponse patchItem(
            HttpClient client, int itemId, String newName, Integer amount, boolean completed
    ) throws IOException, InterruptedException
    {
        HttpResponse<String> response = sendPatch(
                client, new ItemController.PatchItemRequest(itemId, newName, amount, completed)
        );

        ItemController.PatchItemResponse patchItemRequest = mapper.readValue(
                response.body(), ItemController.PatchItemResponse.class
        );

        assertEquals(200, response.statusCode());
        log.info("{}", patchItemRequest);
        return patchItemRequest;
    }

    private HttpResponse<String> sendPatch(HttpClient client, ItemController.PatchItemRequest requestBody)
            throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .method("PATCH", getBodyPublisher(requestBody))
            .build();

        return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private ItemController.PostItemResponse createItem(HttpClient client, String itemName)
            throws IOException, InterruptedException
    {
        return createItem(client, itemName, null);
    }

    private ItemController.PostItemResponse createItem(HttpClient client, String itemName, List<Integer> labelIds)
            throws IOException, InterruptedException
    {
        ItemController.PostItemRequest requestBody = new ItemController.PostItemRequest(
                ITEM_LIST_ID, itemName, labelIds, null
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
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

    // The names of the items on the test list, in the order they are sent.
    private List<String> itemNamesInOrder(HttpClient client) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .GET()
            .build();
        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, response.statusCode());

        List<ItemController.GetItemListResponse> lists = mapper.readValue(
                response.body(),
                mapper.getTypeFactory().constructCollectionType(List.class, ItemController.GetItemListResponse.class)
        );
        return lists.stream()
                .filter(list -> list.id() == ITEM_LIST_ID)
                .findFirst()
                .orElseThrow()
                .items().stream()
                .map(ItemController.GetItemResponse::name)
                .toList();
    }

    private ItemController.GetItemResponse getItem(HttpClient client, int itemId)
            throws IOException, InterruptedException
    {
        ItemController.GetItemRequest requestBody = new ItemController.GetItemRequest(itemId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
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
