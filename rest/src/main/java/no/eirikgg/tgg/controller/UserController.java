package no.eirikgg.tgg.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.validation.Validator;
import no.eirikgg.tgg.datamodel.Authorization;
import no.eirikgg.tgg.datamodel.Email;
import no.eirikgg.tgg.datamodel.Person;
import no.eirikgg.tgg.datamodel.PhoneNumber;
import no.eirikgg.tgg.datamodel.User;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.db.repository.EmailRepository;
import no.eirikgg.tgg.errors.NoEmailConnectedToVerificationUuidException;
import no.eirikgg.tgg.errors.UsernameAlreadyExistsException;
import no.eirikgg.tgg.service.AuthenticationService;
import no.eirikgg.tgg.service.EmailService;
import no.eirikgg.tgg.service.PasswordService;
import no.eirikgg.tgg.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
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
    public static final String PATH_VERIFY_EMAIL = "/verify_email";


    public static final String PARAM_FIRSTNAME = "firstname";
    public static final String PARAM_LASTNAME = "lastname";
    public static final String PARAM_BIRTHDATE = "birthdate";
    public static final String PARAM_GENDER = "gender";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_EMAIL = "email";
    public static final String PARAM_COUNTRYCODE = "countrycode";
    public static final String PARAM_PHONENUMBER = "phonenumber";
    public static final String PARAM_VERIFY_EMAIL = "key";


    private UserService userService;
    private AuthenticationService authenticationService;
    private EmailService emailService;
    private ObjectMapper mapper = new ObjectMapper();

    public UserController(
            UserService userService,
            AuthenticationService authenticationService,
            EmailService emailService
    ) {
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.emailService = emailService;
    }

    public static void setup(JavalinConfig config, Db db, String emailHost, String emailPort) {
        UserController userController = new UserController(
                new UserService(db),
                new AuthenticationService(db),
                new EmailService(db, emailHost, emailPort)
        );
        config.routes.post(UserController.PATH_USER, userController::createUser);
        config.routes.post(UserController.PATH_LOGIN, userController::login);
        config.routes.get(UserController.PATH_SESSION_IS_VALID, userController::isValid);
        config.routes.get(UserController.PATH_IS_USERNAME_AVAILABLE, userController::isUsernameAvailable);
        config.routes.post(UserController.PATH_VERIFY_EMAIL, userController::verifyEmail);
    }


    public void createUser(Context ctx) throws JsonProcessingException, UsernameAlreadyExistsException {
        JsonNode json = mapper.readTree(ctx.body());

        String firstName = json.get(PARAM_FIRSTNAME).asText();
        String lastName = json.get(PARAM_LASTNAME).asText();
        LocalDate birthdate = LocalDate.parse(json.get(PARAM_BIRTHDATE).asText(), Person.birthdateFormat);
        Person.Gender gender = Person.Gender.valueOf(json.get(PARAM_GENDER).asText());
        String username = json.get(PARAM_EMAIL).asText();
        String password = json.get(PARAM_PASSWORD).asText();
        String hashedPassword = PasswordService.hashPassword(password);
        String email = json.get(PARAM_EMAIL).asText();
        String countryCode = json.get(PARAM_COUNTRYCODE).asText();
        String phoneNumber = json.get(PARAM_PHONENUMBER).asText();

        UUID emailVerificationUuid = UUID.randomUUID();
        String frontendUrl = Optional.ofNullable(ctx.header("Origin"))
            .orElseThrow(() -> new BadRequestResponse("Missing Origin header"));
        String verificationUrl = URI.create(frontendUrl)
            .resolve(PATH_VERIFY_EMAIL + "?" + PARAM_VERIFY_EMAIL + "=" + emailVerificationUuid)
            .toString();

        User userRequest = new User(
                new Person(firstName, lastName, birthdate, gender),
                new Authorization(username, hashedPassword),
                new Email(email, emailVerificationUuid),
                new PhoneNumber(countryCode, phoneNumber)
        );

        log.info("Verification URI: {}", verificationUrl);

        User persistedUser = userService.persistUser(userRequest);
        UUID sessionId = authenticationService.login(username, password).orElseThrow();
        emailService.createUserVerificationEmail(sessionId, persistedUser, verificationUrl);
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

        boolean isUsernameAvailable = userService.isUsernameAvailable(username);

        ctx.status(
            isUsernameAvailable ? HttpStatus.OK : HttpStatus.NOT_ACCEPTABLE
        );
    }


    public void verifyEmail(Context ctx) throws NoEmailConnectedToVerificationUuidException {
        String verificationUuid = ctx.queryParam(PARAM_VERIFY_EMAIL);

        try {
            UUID uuid = UUID.fromString(verificationUuid);

            log.info("Verifying email address with verification key \"{}\"", uuid);
            userService.verifyEmailAddressUsingUuid(uuid);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not a valid verification key: " + verificationUuid);
        }
    }
}
