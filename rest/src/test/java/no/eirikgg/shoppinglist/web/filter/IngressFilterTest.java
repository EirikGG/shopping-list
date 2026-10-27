package no.eirikgg.shoppinglist.web.filter;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.TestDb;
import no.eirikgg.shoppinglist.web.controller.GeneralController;
import no.eirikgg.shoppinglist.web.controller.ItemController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngressFilterTest {

    private final TestDb db = TestDb.createMigratedInstance();
    private final TestConfig testConfig = new TestConfig(true, false);

    @AfterEach
    void closeDb() {
        db.close();
    }

    @Test
    void requestWithoutUser_isUnauthorized() {
        JavalinTest.test(TestApp.create(db), testConfig, (server, client) -> {
            assertEquals(401, client.get(ItemController.PATH_ITEM_LIST).code());
        });
    }

    @Test
    void ping_needsNoUser() {
        JavalinTest.test(TestApp.create(db), testConfig, (server, client) -> {
            assertEquals(200, client.get(GeneralController.PATH_PING).code());
        });
    }

    // Anyone else who could reach the port could otherwise act as any user.
    @Test
    void userHeadersFromAnotherAddress_areForbidden() {
        Javalin app = TestApp.create(db, "10.255.255.1");
        JavalinTest.test(app, testConfig, (server, client) -> {
            assertEquals(403, getItemLists(server, TestApp.USER_ID, TestApp.USER_NAME).statusCode());
        });
    }

    @Test
    void userHeadersFromTheTrustedProxy_areAccepted() {
        Javalin app = TestApp.create(db, "127.0.0.1");
        JavalinTest.test(app, testConfig, (server, client) -> {
            assertEquals(200, getItemLists(server, TestApp.USER_ID, TestApp.USER_NAME).statusCode());
        });
    }

    @Test
    void firstRequest_createsThePerson_andTheNameFollowsHomeAssistant() {
        JavalinTest.test(TestApp.create(db), testConfig, (server, client) -> {
            assertEquals(200, getItemLists(server, TestApp.USER_ID, "Before").statusCode());
            assertEquals(List.of("Before"), personNames(TestApp.USER_ID));

            assertEquals(200, getItemLists(server, TestApp.USER_ID, "After").statusCode());
            assertEquals(List.of("After"), personNames(TestApp.USER_ID));
        });
    }

    @Test
    void userWithoutDisplayName_isNamedByUsername() {
        JavalinTest.test(TestApp.create(db), testConfig, (server, client) -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + ItemController.PATH_ITEM_LIST))
                    .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
                    .header(IngressFilter.HEADER_USER_NAME, "username")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals(List.of("username"), personNames(TestApp.USER_ID));
        });
    }

    // Over 127.0.0.1 rather than localhost, so the address the server sees is
    // known.
    private HttpResponse<String> getItemLists(Javalin server, String haUserId, String displayName)
            throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder(), haUserId, displayName)
                .uri(URI.create("http://127.0.0.1:" + server.port() + ItemController.PATH_ITEM_LIST))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<String> personNames(String haUserId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            return new DbUtils().select(conn,
                    "select name from person where ha_user_id = ?",
                    haUserId,
                    rs -> rs.getString("name").orElseThrow()
            );
        }
    }
}
