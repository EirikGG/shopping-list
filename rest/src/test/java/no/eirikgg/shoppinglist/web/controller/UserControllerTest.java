package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.datamodel.EmailTemplate;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserControllerTest {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final ObjectMapper mapper = Json.MAPPER;


    public final static String FIRSTNAME = "Eirik";
    public final static String LASTNAME = "Gustafsson";
    public final static LocalDate BIRTHDATE = LocalDate.of(1994, 05, 14);
    public final static Person.Gender GENDER = Person.Gender.MALE;
    public final static String EMAIL = "eirikgg94@gmail.com";
    public final static String PASSWORD = "test1234";


    @Test
    void createMultipleUsers_expectRateLimiting() {
        EmbeddedPostgresDb db = EmbeddedPostgresDb.createMigratedInstance();
        EmailSenderFake fakeEmailSender = new EmailSenderFake();
        Javalin app = Main.setupJavalinApp(db, fakeEmailSender);
        TestConfig testConfig = new TestConfig(true, false);
        JavalinTest.test(app, testConfig, (server, client) -> {

            boolean threwException = false;
            int numberOfUsersToCreate = 20;
            for (int i = 0; i < numberOfUsersToCreate; i++) {
                String email = "unit-test-%d@test.com".formatted(i);
                Response userResponse = postUser(client, email);

                int status = userResponse.code();
                if (200 == status) {
                    // Created a user ok
                }
                else if (429 == status) {
                    threwException = true;
                    break;
                }
            }

            if (!threwException) {
                fail("Created " + numberOfUsersToCreate + " users without hitting rate limiter");
            }
        });
    }

    public static Response postUser(HttpClient client, String email) {
        UserController.PostCreateUserRequest request = new UserController.PostCreateUserRequest(
                FIRSTNAME,
                LASTNAME,
                BIRTHDATE,
                GENDER,
                PASSWORD,
                email
        );
        Response userResponse = client.post(UserController.PATH_USER, request);
        return userResponse;
    }


    @Test
    void createUserAndLogin_expectLoggedInOk() {
        EmbeddedPostgresDb db = EmbeddedPostgresDb.createMigratedInstance();
        EmailSenderFake fakeEmailSender = new EmailSenderFake();
        Javalin app = Main.setupJavalinApp(db, fakeEmailSender);
        TestConfig testConfig = new TestConfig(true, false);
        JavalinTest.test(app, testConfig, (server, client) -> {

            createUserAndExpectStatus(client, 200);
            verifyEmail(client, EMAIL, fakeEmailSender);
            createUserAndExpectStatus(client, 400);

            Response loginResponse = loginWithUser(client);
            UserController.PostLoginResponse okSessionBodyJson = mapper.readValue(
                    loginResponse.getBody().string(),
                    UserController.PostLoginResponse.class
            );

            UUID okSessionId = okSessionBodyJson.session();


            Response duplicateUsernameResponse = createUserAndExpectStatus(client, 400);


            log.info("duplicateUsernameResponse: {}", duplicateUsernameResponse.getBody().string());
            assertEquals(400, duplicateUsernameResponse.code());
            assertTrue(duplicateUsernameResponse.getBody().string().contains(Error.DUPLICATE_USERNAME.name()));


            testSessionId(client, okSessionId, 200);

            testLogout(client, okSessionId);
            testSessionId(client, okSessionId, 401);
        });
    }

    private void testLogout(
            HttpClient client, UUID sessionId
    ) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                 .uri(URI.create(client.getOrigin() + UserController.PATH_LOGOUT))
                 .header(Header.AUTHORIZATION, sessionId.toString())
                 .POST(HttpRequest.BodyPublishers.noBody())
                 .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(), "Should be logged out");
    }

    private void verifyEmail(HttpClient client, String email, EmailSenderFake fakeEmailSender) throws InterruptedException {

        String emailBody = null;
        for (int i = 0; i < 20; i++) {
            if (null != fakeEmailSender.getEmailMessage()) {
                emailBody = fakeEmailSender.getEmailMessage().getBody();
                break;
            }

            Thread.sleep(500);
        }

        if (null == emailBody) {
            throw new IllegalStateException("Emailsender never sendt the email");
        }

        int verificationCode = EmailTemplate.NewUser.getVerificationCodeFromBody(emailBody);
        UserController.PostVerifyEmailRequest request = new UserController.PostVerifyEmailRequest(
                email, verificationCode
        );
        client.post(UserController.PATH_VERIFY_EMAIL, request);
    }


    private static void testSessionId(
            HttpClient client, UUID okSessionId, int expectedStatusCode
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

        assertEquals(expectedStatusCode, response.statusCode(), "Should be a valid session id");
    }


    private Response loginWithUser(HttpClient client) {
        UserController.PostLoginRequest request = new UserController.PostLoginRequest(EMAIL, PASSWORD);
        Response loginResponse = client.post(UserController.PATH_LOGIN, request);

        log.info("login response: {}", loginResponse.getBody().string());
        assertEquals(200, loginResponse.code());

        request = new UserController.PostLoginRequest(EMAIL, "test124");
        Response failedLoginResponse = client.post(UserController.PATH_LOGIN, request);

        log.info("login response: {}", failedLoginResponse.getBody().string());
        assertEquals(401, failedLoginResponse.code());
        return loginResponse;
    }


    private static Response createUserAndExpectStatus(HttpClient client, int status) {
        Response userResponse = postUser(client, EMAIL);
        assertEquals(status, userResponse.code());
        return userResponse;
    }


}