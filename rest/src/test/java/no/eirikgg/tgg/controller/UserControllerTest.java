package no.eirikgg.tgg.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import io.javalin.testtools.TestConfig;
import no.eirikgg.tgg.Main;
import no.eirikgg.tgg.Utils;
import no.eirikgg.tgg.db.EmbeddedPostgresDb;
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
            Response response = client.post(Utils.PATH_USER, Map.ofEntries(
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

            assertEquals(200, response.code());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.getBody().string());

            log.info("Created user: {}", json);
        });
    }
}