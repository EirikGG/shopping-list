package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LabelControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private TestDb db;
    private Javalin app;
    private TestConfig testConfig;

    private final ObjectMapper mapper = Json.MAPPER;
    // Labels belong to a list; this is the list from t0001.
    private final int ITEM_LIST_ID;

    public LabelControllerTest() throws SQLException {
        db = TestDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-list.sql");
        try (Connection conn = db.getConnection()) {
            ITEM_LIST_ID = new DbUtils().selectSingle(conn,
                    "select item_list_id from item_list where name = ?",
                    "unit-test-list",
                    rs -> rs.getInt("item_list_id").orElseThrow()
            ).orElseThrow();
        }

        testConfig = new TestConfig(true, false);
        app = TestApp.create(db);
    }

    @AfterEach
    void closeDb() {
        db.close();
    }

    @Test
    void createLabel_patchIt_andDeleteIt() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            String labelName = "test-label1";
            LabelController.PostLabelResponse label = createLabel(client, labelName);
            LabelController.GetLabelResponse getResponse = getLabel(client, label.labelId());
            assertNotNull(getResponse);
            assertEquals(label.labelId(), getResponse.labelId());
            assertEquals(labelName, getResponse.name());
            assertNotNull(getResponse.created());

            String labelName2 = "test-label2";
            patchLabel(client, label.labelId(), labelName2);
            LabelController.GetLabelResponse patchedResponse = getLabel(client, label.labelId());
            assertNotNull(patchedResponse);
            assertEquals(labelName2, patchedResponse.name());

            deleteLabel(client, label.labelId());
            assertNull(getLabel(client, label.labelId()));
        });
    }

    @Test
    void getLabelList_returnsActiveLabelsSortedByName() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            createLabel(client, "list-label-b");
            createLabel(client, "list-label-a");
            LabelController.PostLabelResponse deleted = createLabel(client, "list-label-deleted");
            deleteLabel(client, deleted.labelId());

            List<String> names = getLabelList(client).stream()
                    .map(LabelController.GetLabelResponse::name)
                    .filter(name -> name.startsWith("list-label-"))
                    .toList();

            assertEquals(List.of("list-label-a", "list-label-b"), names);
        });
    }

    private List<LabelController.GetLabelResponse> getLabelList(HttpClient client)
            throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + LabelController.PATH_LABEL_LIST
                    + "?" + LabelController.PARAM_ITEM_LIST_ID + "=" + ITEM_LIST_ID))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .GET()
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        return mapper.readValue(response.body(), new TypeReference<>() {});
    }

    private void deleteLabel(HttpClient client, int labelId) throws IOException, InterruptedException {
        LabelController.DeleteLabelRequest requestBody = new LabelController.DeleteLabelRequest(labelId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + LabelController.PATH_LABEL))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .method("DELETE", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private void patchLabel(HttpClient client, int labelId, String newName)
            throws IOException, InterruptedException
    {
        LabelController.PatchLabelRequest requestBody = new LabelController.PatchLabelRequest(labelId, newName);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + LabelController.PATH_LABEL))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .method("PATCH", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private LabelController.PostLabelResponse createLabel(HttpClient client, String labelName)
            throws IOException, InterruptedException
    {
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

        LabelController.PostLabelResponse postLabelResponse = mapper.readValue(
                response.body(), LabelController.PostLabelResponse.class
        );

        log.info("{}", postLabelResponse);
        return postLabelResponse;
    }

    private LabelController.GetLabelResponse getLabel(HttpClient client, int labelId)
            throws IOException, InterruptedException
    {
        LabelController.GetLabelRequest requestBody = new LabelController.GetLabelRequest(labelId);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(client.getOrigin() + LabelController.PATH_LABEL))
            .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
            .method("GET", getBodyPublisher(requestBody))
            .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        // ResourceNotFoundException is currently mapped to 401 in ExceptionHandlers
        if (200 != response.statusCode()) {
            return null;
        }

        LabelController.GetLabelResponse getLabelResponse = mapper.readValue(
                response.body(), LabelController.GetLabelResponse.class
        );

        log.info("{}", getLabelResponse);
        return getLabelResponse;
    }

    private HttpRequest.BodyPublisher getBodyPublisher(Object requestObject) throws JsonProcessingException {
        String requestBody = mapper.writeValueAsString(requestObject);
        return HttpRequest.BodyPublishers.ofString(requestBody);
    }
}
