package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Header;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;
import no.eirikgg.shoppinglist.db.TestDb;
import org.junit.jupiter.api.AfterEach;
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

    private TestDb db;
    private Javalin app;
    private TestConfig testConfig;

    private final String LIST_NAME = "unit-test-list";
    private final ObjectMapper mapper = Json.MAPPER;

    public ItemListControllerTest() {
        db = TestDb.createMigratedInstance();

        testConfig = new TestConfig(true, false);
        app = TestApp.create(db);
    }

    @AfterEach
    void closeDb() {
        db.close();
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

    // Every Home Assistant user sees and changes every list.
    @Test
    void listsAreSharedByAllUsers() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            ItemController.PostItemListResponse created = createItemList(client);

            List<ItemController.GetItemListResponse> seenBySecond = mapper.readValue(
                    sendAsSecondUser(client, "GET", ItemController.PATH_ITEM_LIST, null).body(),
                    mapper.getTypeFactory().constructCollectionType(
                            List.class, ItemController.GetItemListResponse.class
                    )
            );
            assertEquals(List.of(created.id()), seenBySecond.stream().map(list -> list.id()).toList());

            HttpResponse<String> addItem = sendAsSecondUser(client, "POST", ItemController.PATH_ITEM,
                    new ItemController.PostItemRequest(created.id(), "milk", null, null));
            assertEquals(201, addItem.statusCode());

            HttpResponse<String> rename = sendAsSecondUser(client, "PATCH", ItemController.PATH_ITEM_LIST,
                    new ItemController.PatchItemListRequest(created.id(), "renamed"));
            assertEquals(200, rename.statusCode());

            ItemController.GetItemListResponse seenByFirst = getItemList(client).get(0);
            assertEquals("renamed", seenByFirst.name());
            assertEquals(List.of("milk"), seenByFirst.items().stream().map(item -> item.name()).toList());

            HttpResponse<String> delete = sendAsSecondUser(client, "DELETE", ItemController.PATH_ITEM_LIST,
                    new ItemController.DeleteItemListRequest(created.id()));
            assertEquals(200, delete.statusCode());
            assertEquals(List.of(), getItemList(client));
        });
    }

    @Test
    void renamingOrDeletingAMissingList_isNotFound() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            assertEquals(404, sendAsSecondUser(client, "PATCH", ItemController.PATH_ITEM_LIST,
                    new ItemController.PatchItemListRequest(999_999, "renamed")).statusCode());
            assertEquals(404, sendAsSecondUser(client, "DELETE", ItemController.PATH_ITEM_LIST,
                    new ItemController.DeleteItemListRequest(999_999)).statusCode());
        });
    }

    private HttpResponse<String> sendAsSecondUser(
            io.javalin.testtools.HttpClient client, String method, String path, Object body
    ) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = null == body
                ? HttpRequest.BodyPublishers.noBody()
                : getBodyPublisher(body);
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder(), TestApp.SECOND_USER_ID, TestApp.SECOND_USER_NAME)
                .uri(URI.create(client.getOrigin() + path))
                .method(method, publisher)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }


    private void updateItemList(io.javalin.testtools.HttpClient client, int itemListId, String newName)
            throws IOException, InterruptedException
    {
        ItemController.PatchItemListRequest requestBody = new ItemController.PatchItemListRequest(
                itemListId, newName
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM_LIST))
                .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
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
                .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
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
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
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
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
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
