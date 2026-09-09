package no.eirikgg.tgg.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import no.eirikgg.tgg.datamodel.Authorization;
import no.eirikgg.tgg.datamodel.Email;
import no.eirikgg.tgg.datamodel.Person;
import no.eirikgg.tgg.datamodel.PhoneNumber;
import no.eirikgg.tgg.datamodel.User;
import no.eirikgg.tgg.errors.UsernameAlreadyExistsException;
import no.eirikgg.tgg.service.AuthenticationService;
import no.eirikgg.tgg.service.PasswordService;
import no.eirikgg.tgg.service.UserService;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserController {
    public static final String USER_PATH = "/user";
    public static final String LOGIN_PATH = "/login";

    private UserService userService;
    private AuthenticationService authenticationService;
    private ObjectMapper mapper = new ObjectMapper();

    public UserController(UserService userService, AuthenticationService authenticationService) {
        this.userService = userService;
        this.authenticationService = authenticationService;
    }


    public void createUser(Context ctx) throws JsonProcessingException, UsernameAlreadyExistsException {
        JsonNode json = mapper.readTree(ctx.body());

        String firstName = json.get("firstName").asText();
        String lastName = json.get("lastName").asText();
        LocalDate birthdate = LocalDate.parse(
                json.get("birthdate").asText(),
                Person.birthdateFormat
        );
        Person.Gender gender = Person.Gender.valueOf(json.get("gender").asText());
        String username = json.get("username").asText();
        String password = PasswordService.hashPassword(json.get("password").asText());
        String email = json.get("email").asText();
        String countryCode = json.get("countryCode").asText();
        String phoneNumber = json.get("phoneNumber").asText();

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
        String username = json.get("username").asText();
        String password = json.get("password").asText();

        Optional<UUID> sessionId = authenticationService.login(username, password);

        if (sessionId.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            throw new UnauthorizedResponse("UNAUTHORIZED");
        }

        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("sessionId", sessionId.get()));
    }
}
