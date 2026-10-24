package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.Main;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private EmbeddedPostgresDb db;
    private EmailSenderFake fakeEmailSender;
    private Javalin app;
    private TestConfig testConfig;

    private final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ObjectMapper mapper = Json.MAPPER;

    public StoreControllerTest() {
        db = EmbeddedPostgresDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-user-and-login.sql");

        fakeEmailSender = new EmailSenderFake();
        testConfig = new TestConfig(true, false);
        app = Main.setupJavalinApp(db, fakeEmailSender);
    }

    @Test
    void createStore_patchIt_andDeleteIt() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            String storeName = "test-store1";
            StoreController.PostStoreResponse store = createStore(client, storeName);
            StoreController.GetStoreResponse getResponse = getStore(client, store.storeId());
            assertNotNull(getResponse);
            assertEquals(store.storeId(), getResponse.storeId());
            assertEquals(storeName, getResponse.name());
            assertNotNull(getResponse.created());

            String storeName2 = "test-store2";
            patchStore(client, store.storeId(), storeName2);
            StoreController.GetStoreResponse patchedResponse = getStore(client, store.storeId());
            assertNotNull(patchedResponse);
            assertEquals(storeName2, patchedResponse.name());

            deleteStore(client, store.storeId());
            assertNull(getStore(client, store.storeId()));
        });
    }

    @Test
    void getStoreList_returnsActiveStoresSortedByName() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            createStore(client, "list-store-b");
            createStore(client, "list-store-a");
            StoreController.PostStoreResponse deleted = createStore(client, "list-store-deleted");
            deleteStore(client, deleted.storeId());

            List<String> names = getStoreList(client).stream()
                    .map(StoreController.GetStoreResponse::name)
                    .filter(name -> name.startsWith("list-store-"))
                    .toList();

            assertEquals(List.of("list-store-a", "list-store-b"), names);
        });
    }

    private List<StoreController.GetStoreResponse> getStoreList(HttpClient client)
            throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + StoreController.PATH_STORE_LIST))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .GET()
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        return mapper.readValue(response.body(), new TypeReference<>() {});
    }

    private void deleteStore(HttpClient client, int storeId) throws IOException, InterruptedException {
        StoreController.DeleteStoreRequest requestBody = new StoreController.DeleteStoreRequest(storeId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + StoreController.PATH_STORE))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .method("DELETE", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private void patchStore(HttpClient client, int storeId, String newName)
            throws IOException, InterruptedException
    {
        StoreController.PatchStoreRequest requestBody = new StoreController.PatchStoreRequest(storeId, newName);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + StoreController.PATH_STORE))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .method("PATCH", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private StoreController.PostStoreResponse createStore(HttpClient client, String storeName)
            throws IOException, InterruptedException
    {
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

        StoreController.PostStoreResponse postStoreResponse = mapper.readValue(
                response.body(), StoreController.PostStoreResponse.class
        );

        log.info("{}", postStoreResponse);
        return postStoreResponse;
    }

    private StoreController.GetStoreResponse getStore(HttpClient client, int storeId)
            throws IOException, InterruptedException
    {
        StoreController.GetStoreRequest requestBody = new StoreController.GetStoreRequest(storeId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + StoreController.PATH_STORE))
            .header(Header.AUTHORIZATION, SESSION_ID.toString())
            .method("GET", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        // ResourceNotFoundException is currently mapped to 401 in ExceptionHandlers
        if (200 != response.statusCode()) {
            return null;
        }

        StoreController.GetStoreResponse getStoreResponse = mapper.readValue(
                response.body(), StoreController.GetStoreResponse.class
        );

        log.info("{}", getStoreResponse);
        return getStoreResponse;
    }

    private HttpRequest.BodyPublisher getBodyPublisher(Object requestObject) throws JsonProcessingException {
        String requestBody = mapper.writeValueAsString(requestObject);
        return HttpRequest.BodyPublishers.ofString(requestBody);
    }
}
