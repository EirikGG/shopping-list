package no.eirikgg.tgg.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.tgg.datamodel.Authorization;
import no.eirikgg.tgg.datamodel.Email;
import no.eirikgg.tgg.datamodel.Person;
import no.eirikgg.tgg.datamodel.PhoneNumber;
import no.eirikgg.tgg.datamodel.User;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.errors.UsernameAlreadyExistsException;
import no.eirikgg.tgg.service.AuthenticationService;
import no.eirikgg.tgg.service.PasswordService;
import no.eirikgg.tgg.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String PATH_USER = "/user";
    public static final String PATH_LOGIN = "/login_user";
    public static final String PATH_SESSION_IS_VALID = "/session_is_valid";
    public static final String PATH_IS_USERNAME_AVAILABLE = "/is_username_available";


    public static final String PARAM_FIRSTNAME = "firstname";
    public static final String PARAM_LASTNAME = "lastname";
    public static final String PARAM_BIRTHDATE = "birthdate";
    public static final String PARAM_GENDER = "gender";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_EMAIL = "email";
    public static final String PARAM_COUNTRYCODE = "countrycode";
    public static final String PARAM_PHONENUMBER = "phonenumber";


    private UserService userService;
    private AuthenticationService authenticationService;
    private ObjectMapper mapper = new ObjectMapper();

    public UserController(UserService userService, AuthenticationService authenticationService) {
        this.userService = userService;
        this.authenticationService = authenticationService;
    }

    public static void setup(JavalinConfig config, Db db) {
        UserController userController = new UserController(
                new UserService(db),
                new AuthenticationService(db)
        );
        config.routes.post(UserController.PATH_USER, userController::createUser);
        config.routes.post(UserController.PATH_LOGIN, userController::login);
        config.routes.get(UserController.PATH_SESSION_IS_VALID, userController::isValid);
        config.routes.get(UserController.PATH_IS_USERNAME_AVAILABLE, userController::isUsernameAvailable);
    }


    public void createUser(Context ctx) throws JsonProcessingException, UsernameAlreadyExistsException {
        JsonNode json = mapper.readTree(ctx.body());

        String firstName = json.get(PARAM_FIRSTNAME).asText();
        String lastName = json.get(PARAM_LASTNAME).asText();
        LocalDate birthdate = LocalDate.parse(json.get(PARAM_BIRTHDATE).asText(), Person.birthdateFormat);
        Person.Gender gender = Person.Gender.valueOf(json.get(PARAM_GENDER).asText());
        String username = json.get(PARAM_EMAIL).asText();
        String password = PasswordService.hashPassword(json.get(PARAM_PASSWORD).asText());
        String email = json.get(PARAM_EMAIL).asText();
        String countryCode = json.get(PARAM_COUNTRYCODE).asText();
        String phoneNumber = json.get(PARAM_PHONENUMBER).asText();

        User userRequest = new User(
                new Person(firstName, lastName, birthdate, gender),
                new Authorization(username, password),
                new Email(email),
                new PhoneNumber(countryCode, phoneNumber)
        );


        User persistedUser = userService.persistUser(userRequest);
        ctx.status(HttpStatus.OK).json(persistedUser);
    }

    public void login(Context ctx) throws JsonProcessingException {
        JsonNode json = mapper.readTree(ctx.body());
        String username = json.get(PARAM_USERNAME).asText();
        String password = json.get(PARAM_PASSWORD).asText();

        Optional<UUID> sessionId = authenticationService.login(username, password);

        if (sessionId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            throw new UnauthorizedResponse("UNAUTHORIZED");
        }

        ctx.status(HttpStatus.OK);
        ctx.json(Map.of(AuthenticationFilter.PARAM_SESSION_ID, sessionId.get()));
    }

    public void isValid(Context ctx) {
        UUID sessionId = AuthenticationService.getSessionIdFromRequest(ctx).orElseThrow(
                () -> new UnauthorizedResponse("UNAUTHORIZED")
        );

        boolean isValid = authenticationService.isValidSessionId(sessionId);
        if (!isValid) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            throw new UnauthorizedResponse("UNAUTHORIZED");
        }

        ctx.status(HttpStatus.OK);
    }


    public void isUsernameAvailable(Context ctx) {
        String username = ctx.header(PARAM_USERNAME);

        log.info("Checking if username {} is available", username);
        System.out.println(username );

        boolean isUsernameAvailable = userService.isUsernameAvailable(username);

        ctx.status(
            isUsernameAvailable ? HttpStatus.OK : HttpStatus.NOT_ACCEPTABLE
        );
    }
}
