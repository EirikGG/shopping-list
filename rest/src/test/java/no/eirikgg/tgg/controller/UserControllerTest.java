package no.eirikgg.tgg.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import io.javalin.testtools.TestConfig;
import no.eirikgg.tgg.Main;
import no.eirikgg.tgg.db.EmbeddedPostgresDb;
import no.eirikgg.tgg.errors.Error;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Test
    void createUserAndLogin_expectLoggedInOk() {
        EmbeddedPostgresDb db = EmbeddedPostgresDb.createMigratedInstance();
        Javalin app = Main.setupJavalinApp(db);
        TestConfig testConfig = new TestConfig(true, false);
        JavalinTest.test(app, testConfig, (server, client) -> {
            Response userResponse = client.post(UserController.USER_PATH, Map.ofEntries(
                    Map.entry("firstName", "Eirik"),
                    Map.entry("lastName", "Gustafsson"),
                    Map.entry("birthdate", "1994-05-14"),
                    Map.entry("gender", "MALE"),
                    Map.entry("username", "eirikgg94@gmail.com"),
                    Map.entry("password", "test1234"),
                    Map.entry("email", "eirikgg94@gmail.com"),
                    Map.entry("countryCode", "NO"),
                    Map.entry("phoneNumber", "99489816")
            ));

            assertEquals(200, userResponse.code());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(userResponse.getBody().string());

            JsonNode authorization = json.get("authorization");
            String username = authorization.get("username").asText();
            String password = authorization.get("password").asText();

            assertNotEquals("test1234", password);

            Response loginResponse = client.post(UserController.LOGIN_PATH, Map.ofEntries(
                    Map.entry("username", username),
                    Map.entry("password", "test1234")
            ));

            log.info("login response: {}", loginResponse.getBody().string());
            assertEquals(200, loginResponse.code());

            Response failedLoginResponse = client.post(UserController.LOGIN_PATH, Map.ofEntries(
                    Map.entry("username", username),
                    Map.entry("password", "test123")
            ));

            log.info("login response: {}", failedLoginResponse.getBody().string());
            assertEquals(401, failedLoginResponse.code());


            Response duplicateUsernameResponse = client.post(UserController.USER_PATH, Map.ofEntries(
                    Map.entry("firstName", "Eirik"),
                    Map.entry("lastName", "Gustafsson"),
                    Map.entry("birthdate", "1994-05-14"),
                    Map.entry("gender", "MALE"),
                    Map.entry("username", "eirikgg94@gmail.com"),
                    Map.entry("password", "test1234"),
                    Map.entry("email", "eirikgg94@gmail.com"),
                    Map.entry("countryCode", "NO"),
                    Map.entry("phoneNumber", "99489816")
            ));

            log.info("duplicateUsernameResponse: {}", duplicateUsernameResponse.getBody().string());
            assertEquals(400, duplicateUsernameResponse.code());
            assertTrue(duplicateUsernameResponse.getBody().string().contains(Error.DUPLICATE_USERNAME.name()));
        });
    }
}