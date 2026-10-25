package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.EmailAlreadyVerifiedException;
import no.eirikgg.shoppinglist.errors.InvalidEmailException;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;
import no.eirikgg.shoppinglist.errors.WeakPasswordException;
import no.eirikgg.shoppinglist.errors.WrongEmailVerificationCodeException;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;
import no.eirikgg.shoppinglist.web.filter.RateLimiter;
import no.eirikgg.shoppinglist.service.AuthenticationService;
import no.eirikgg.shoppinglist.service.CredentialValidator;
import no.eirikgg.shoppinglist.service.EmailService;
import no.eirikgg.shoppinglist.service.EmailSender;
import no.eirikgg.shoppinglist.service.PasswordService;
import no.eirikgg.shoppinglist.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String PATH_USER = "/user";
    public static final String PATH_LOGIN = "/login_user";
    public static final String PATH_LOGOUT = "/logout";
    public static final String PATH_SESSION_IS_VALID = "/session_is_valid";
    public static final String PATH_VERIFY_EMAIL = "/verify_email";

    private RateLimiter rateLimiter = new RateLimiter(5, Duration.ofMinutes(1));

    private UserService userService;
    private AuthenticationService authenticationService;
    private EmailService emailService;

    public UserController(
            UserService userService,
            AuthenticationService authenticationService,
            EmailService emailService
    ) {
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.emailService = emailService;
    }

    public static void setup(JavalinConfig config, Db db, EmailSender emailSender) {
        UserController userController = new UserController(
                new UserService(db),
                new AuthenticationService(db),
                new EmailService(db, emailSender)
        );
        config.routes.post(UserController.PATH_USER, userController::createUser);
        config.routes.post(UserController.PATH_LOGIN, userController::login);
        config.routes.get(UserController.PATH_SESSION_IS_VALID, userController::isValid);
        config.routes.post(UserController.PATH_VERIFY_EMAIL, userController::verifyEmail);
        config.routes.post(UserController.PATH_LOGOUT, userController::logout);
    }

    record PostCreateUserRequest(
        String firstname,
        String lastname,
        String password,
        String email
    ) {}
    public void createUser(Context ctx)
            throws UsernameAlreadyExistsException, InvalidSessionException, EmailNotRegisteredException,
            InvalidEmailException, WeakPasswordException {
        rateLimiter.check(ctx);

        PostCreateUserRequest request = ctx.bodyAsClass(PostCreateUserRequest.class);
        String email = CredentialValidator.validateEmail(request.email());
        String password = request.password();
        CredentialValidator.validatePassword(password);
        String hashedPassword = PasswordService.hashPassword(password);

        User persistedUser = userService.createUser(
                request.firstname(),
                request.lastname(),
                email,
                hashedPassword
        );
        UUID sessionId = authenticationService.login(email, password).orElseThrow(
                () -> new RuntimeException("User just created - should be able to login")
        );
        emailService.createUserVerificationEmail(sessionId, persistedUser);
    }

    record PostLoginRequest(String email, String password) {}
    record PostLoginResponse(UUID session) {}
    public void login(Context ctx) throws InvalidSessionException, UserEmailNotVerifiedException {
        PostLoginRequest request = ctx.bodyAsClass(PostLoginRequest.class);
        Optional<UUID> sessionId = authenticationService.login(request.email(), request.password());

        if (sessionId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            throw new UnauthorizedResponse("UNAUTHORIZED");
        }

        PostLoginResponse response = new PostLoginResponse(sessionId.orElseThrow());
        ctx.status(HttpStatus.OK).json(response);
    }

    public void logout(Context ctx) {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        authenticationService.logout(session);
    }

    public void isValid(Context ctx) throws InvalidSessionException, UserEmailNotVerifiedException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        authenticationService.validateSession(session.getSessionId());

        ctx.status(HttpStatus.OK);
    }


    record PostVerifyEmailRequest(String email, int key) {}
    public void verifyEmail(Context ctx) throws EmailNotRegisteredException, EmailAlreadyVerifiedException,
            WrongEmailVerificationCodeException {
        PostVerifyEmailRequest request = ctx.bodyAsClass(PostVerifyEmailRequest.class);
        log.info("Verifying email address {} with code {}", request.email(), request.key());
        userService.verifyEmailAddress(request.email(), request.key());
    }
}
