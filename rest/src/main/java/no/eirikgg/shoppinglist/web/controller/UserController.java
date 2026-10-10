package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.EmailAlreadyVerifiedException;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;
import no.eirikgg.shoppinglist.errors.WrongEmailVerificationCodeException;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;
import no.eirikgg.shoppinglist.web.filter.RateLimiter;
import no.eirikgg.shoppinglist.service.AuthenticationService;
import no.eirikgg.shoppinglist.service.EmailService;
import no.eirikgg.shoppinglist.service.EmailSenderSmtp;
import no.eirikgg.shoppinglist.service.PasswordService;
import no.eirikgg.shoppinglist.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String PATH_USER = "/user";
    public static final String PATH_LOGIN = "/login_user";
    public static final String PATH_SESSION_IS_VALID = "/session_is_valid";
    public static final String PATH_VERIFY_EMAIL = "/verify_email";


    public static final String PARAM_FIRSTNAME = "firstname";
    public static final String PARAM_LASTNAME = "lastname";
    public static final String PARAM_BIRTHDATE = "birthdate";
    public static final String PARAM_GENDER = "gender";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_EMAIL = "email";
    public static final String PARAM_VERIFY_EMAIL = "key";

    private RateLimiter rateLimiter = new RateLimiter(5, Duration.ofMinutes(1));

    private UserService userService;
    private AuthenticationService authenticationService;
    private EmailService emailService;
    private ObjectMapper mapper = Json.MAPPER;

    public UserController(
            UserService userService,
            AuthenticationService authenticationService,
            EmailService emailService
    ) {
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.emailService = emailService;
    }

    public static void setup(JavalinConfig config, Db db, EmailSenderSmtp emailSender) {
        UserController userController = new UserController(
                new UserService(db),
                new AuthenticationService(db),
                new EmailService(db, emailSender)
        );
        config.routes.post(UserController.PATH_USER, userController::createUser);
        config.routes.post(UserController.PATH_LOGIN, userController::login);
        config.routes.get(UserController.PATH_SESSION_IS_VALID, userController::isValid);
        config.routes.post(UserController.PATH_VERIFY_EMAIL, userController::verifyEmail);
    }


    public void createUser(Context ctx) throws JsonProcessingException, UsernameAlreadyExistsException,
            InvalidSessionException, EmailNotRegisteredException {

        rateLimiter.check(ctx);

        JsonNode json = mapper.readTree(ctx.body());

        String firstName = json.get(PARAM_FIRSTNAME).asText();
        String lastName = json.get(PARAM_LASTNAME).asText();
        LocalDate birthdate = LocalDate.parse(json.get(PARAM_BIRTHDATE).asText(), Person.birthdateFormat);
        Person.Gender gender = Person.Gender.valueOf(json.get(PARAM_GENDER).asText());
        String username = json.get(PARAM_EMAIL).asText();
        String password = json.get(PARAM_PASSWORD).asText();
        String hashedPassword = PasswordService.hashPassword(password);

        User userRequest = new User(
                new Person(firstName, lastName, birthdate, gender),
                new Authorization(username, hashedPassword)
        );

        User persistedUser = userService.createUser(userRequest);
        UUID sessionId = authenticationService.login(username, password).orElseThrow(
                () -> new RuntimeException("User just created - should be able to login")
        );
        emailService.createUserVerificationEmail(sessionId, persistedUser);
        ctx.status(HttpStatus.OK).json(persistedUser);
    }

    public void login(Context ctx) throws JsonProcessingException, InvalidSessionException,
            UserEmailNotVerifiedException {
        JsonNode json = mapper.readTree(ctx.body());
        String username = json.get(PARAM_EMAIL).asText();
        String password = json.get(PARAM_PASSWORD).asText();

        Optional<UUID> sessionId = authenticationService.login(username, password);

        if (sessionId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            throw new UnauthorizedResponse("UNAUTHORIZED");
        }

        ctx.status(HttpStatus.OK);
        ctx.json(Map.of(AuthenticationFilter.PARAM_SESSION_ID, sessionId.get()));
    }

    public void isValid(Context ctx) throws InvalidSessionException, UserEmailNotVerifiedException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        authenticationService.validateSession(sessionId);

        ctx.status(HttpStatus.OK);
    }


    public void verifyEmail(Context ctx)
            throws EmailNotRegisteredException, EmailAlreadyVerifiedException, WrongEmailVerificationCodeException,
            JsonProcessingException {
        JsonNode json = mapper.readTree(ctx.body());
        String email = json.get(PARAM_EMAIL).asText();
        Integer verificationCode = json.get(PARAM_VERIFY_EMAIL).asInt();
        if (null == verificationCode || 0 == verificationCode) {
            throw new BadRequestResponse("Missing verification code param");
        }

        log.info("Verifying email address {} with code {}", email, verificationCode);
        userService.verifyEmailAddress(email, verificationCode);
    }
}
