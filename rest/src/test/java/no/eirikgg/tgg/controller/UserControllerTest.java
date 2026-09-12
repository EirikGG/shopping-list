package no.eirikgg.tgg.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import io.javalin.testtools.TestConfig;
import no.eirikgg.tgg.Main;
import no.eirikgg.tgg.db.EmbeddedPostgresDb;
import no.eirikgg.tgg.errors.Error;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final ObjectMapper mapper = new ObjectMapper();




    private final static String FIRSTNAME = "Eirik";
    private final static String LASTNAME = "Gustafsson";
    private final static String BIRTHDATE = "1994-05-14";
    private final static String GENDER = "MALE";
    private final static String USERNAME = "eirikgg94@gmail.com";
    private final static String PASSWORD = "test1234";
    private final static String EMAIL = "eirikgg94@gmail.com";
    private final static String COUNTRYCODE = "NO";
    private final static String PHONENUMBER = "99489816";




    @Test
    void createUserAndLogin_expectLoggedInOk() {
        EmbeddedPostgresDb db = EmbeddedPostgresDb.createMigratedInstance();
        Javalin app = Main.setupJavalinApp(db);
        TestConfig testConfig = new TestConfig(true, false);
        JavalinTest.test(app, testConfig, (server, client) -> {

            isUsernameAvailable(client, USERNAME, 200);
            Response userResponse = createUserAndPersistExpectStatus(client, 200);
            isUsernameAvailable(client, USERNAME, 406);

            Response loginResponse = loginWithUser(client);
            JsonNode okSessionBodyJson = mapper.readTree(loginResponse.getBody().string());
            UUID okSessionId = UUID.fromString(okSessionBodyJson.get(AuthenticationFilter.PARAM_SESSION_ID).asText());


            Response duplicateUsernameResponse = createUserAndPersistExpectStatus(client, 400);



            log.info("duplicateUsernameResponse: {}", duplicateUsernameResponse.getBody().string());
            assertEquals(400, duplicateUsernameResponse.code());
            assertTrue(duplicateUsernameResponse.getBody().string().contains(Error.DUPLICATE_USERNAME.name()));


            testSessionId(client, okSessionId);
        });
    }


    private static void isUsernameAvailable(
            HttpClient client, String username, int expectedStatusCode
    ) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                 .uri(URI.create(client.getOrigin() + UserController.PATH_IS_USERNAME_AVAILABLE))
                 .header(UserController.PARAM_USERNAME, username)
                 .GET()
                 .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(expectedStatusCode, response.statusCode(), "Username available status");
    }


    private static void testSessionId(
            HttpClient client, UUID okSessionId
    ) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                 .uri(URI.create(client.getOrigin() + UserController.PATH_SESSION_IS_VALID))
                 .header(Header.AUTHORIZATION, okSessionId.toString())
                 .GET()
                 .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(), "Should be a valid session id");
    }


    private Response loginWithUser(HttpClient client) {
        Response loginResponse = client.post(UserController.PATH_LOGIN, Map.ofEntries(
                Map.entry(UserController.PARAM_USERNAME, USERNAME),
                Map.entry(UserController.PARAM_PASSWORD, PASSWORD)
        ));

        log.info("login response: {}", loginResponse.getBody().string());
        assertEquals(200, loginResponse.code());

        Response failedLoginResponse = client.post(UserController.PATH_LOGIN, Map.ofEntries(
                Map.entry(UserController.PARAM_USERNAME, USERNAME),
                Map.entry(UserController.PARAM_PASSWORD, "test123")
        ));

        log.info("login response: {}", failedLoginResponse.getBody().string());
        assertEquals(401, failedLoginResponse.code());
        return loginResponse;
    }



    private static Response createUserAndPersistExpectStatus(
            HttpClient client, int status
    ) throws JsonProcessingException {

        Response userResponse = client.post(UserController.PATH_USER, Map.ofEntries(
                Map.entry(UserController.PARAM_FIRSTNAME, FIRSTNAME),
                Map.entry(UserController.PARAM_LASTNAME, LASTNAME),
                Map.entry(UserController.PARAM_BIRTHDATE, BIRTHDATE),
                Map.entry(UserController.PARAM_GENDER, GENDER),
                Map.entry(UserController.PARAM_USERNAME, USERNAME),
                Map.entry(UserController.PARAM_PASSWORD, PASSWORD),
                Map.entry(UserController.PARAM_EMAIL, EMAIL),
                Map.entry(UserController.PARAM_COUNTRYCODE, COUNTRYCODE),
                Map.entry(UserController.PARAM_PHONENUMBER, PHONENUMBER)
        ));

        assertEquals(status, userResponse.code());

        if (200 == userResponse.code()) {
            JsonNode json = mapper.readTree(userResponse.getBody().string());

            JsonNode authorization = json.get("authorization");
            String password = authorization.get(UserController.PARAM_PASSWORD).asText();

            assertNotEquals(UserController.PARAM_PASSWORD, password);
        }

        return userResponse;
    }
}