package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.TestDb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLinkControllerTest {

    private final TestDb db = TestDb.createMigratedInstance();
    private final TestConfig testConfig = new TestConfig(true, false);
    private final ObjectMapper mapper = Json.MAPPER;
    private final Javalin app;
    private final int itemListId;

    ItemLinkControllerTest() throws SQLException {
        new LiquibaseMigrator().migrate(db, "db/updates/t0001-create-test-list.sql");
        try (Connection conn = db.getConnection()) {
            itemListId = new DbUtils().selectSingle(conn,
                    "select item_list_id from item_list where name = ?",
                    "unit-test-list",
                    rs -> rs.getInt("item_list_id").orElseThrow()
            ).orElseThrow();
        }
        app = TestApp.create(db);
    }

    @AfterEach
    void closeDb() {
        db.close();
    }

    @Test
    void links_areAddedListedAndDeleted() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int itemId = createItem(client);

            HttpResponse<String> recipe = addLink(client, itemId, "https://example.com/recipe", " Pancakes ");
            assertEquals(201, recipe.statusCode());
            ItemController.ItemLinkResponse link = mapper.readValue(recipe.body(), ItemController.ItemLinkResponse.class);
            assertEquals("https://example.com/recipe", link.url());
            assertEquals("Pancakes", link.title());

            // Without a scheme it is taken to be https; a blank title is none.
            ItemController.ItemLinkResponse typed = mapper.readValue(
                    addLink(client, itemId, "example.com/milk", " ").body(),
                    ItemController.ItemLinkResponse.class
            );
            assertEquals("https://example.com/milk", typed.url());
            assertNull(typed.title());

            assertEquals(List.of(link, typed), getItem(client, itemId).links());

            assertEquals(200, send(client, "DELETE", "{\"id\":" + link.id() + "}").statusCode());
            assertEquals(List.of(typed), getItem(client, itemId).links());
            assertEquals(404, send(client, "DELETE", "{\"id\":" + link.id() + "}").statusCode());
        });
    }

    // Only http and https, so a link can never run script in the app.
    @Test
    void linksThatAreNotWebAddresses_areRefused() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int itemId = createItem(client);
            for (String url : List.of(
                    "javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,<script>", "ftp://example.com",
                    "", "   ", "https://", "file:///etc/passwd"
            )) {
                HttpResponse<String> response = addLink(client, itemId, url, null);
                assertEquals(400, response.statusCode(), url);
                assertTrue(response.body().contains("INVALID_URL"), url);
            }
            assertEquals(List.of(), getItem(client, itemId).links());
        });
    }

    @Test
    void linkingToAMissingItem_isNotFound() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            assertEquals(404, addLink(client, 999_999, "https://example.com", null).statusCode());
        });
    }


    private int createItem(HttpClient client) throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(new ItemController.PostItemRequest(itemListId, "milk", null))
                ))
                .build();
        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), ItemController.PostItemResponse.class).id();
    }

    private HttpResponse<String> addLink(HttpClient client, int itemId, String url, String title)
            throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + ItemLinkController.PATH_ITEM_LINK))
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(new ItemLinkController.PostItemLinkRequest(itemId, url, title))
                ))
                .build();
        return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(HttpClient client, String method, String body)
            throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + ItemLinkController.PATH_ITEM_LINK))
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private ItemController.GetItemResponse getItem(HttpClient client, int itemId)
            throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
                .method("GET", HttpRequest.BodyPublishers.ofString("{\"id\":" + itemId + "}"))
                .build();
        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), ItemController.GetItemResponse.class);
    }
}
