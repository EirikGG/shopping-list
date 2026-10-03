package no.eirikgg.shoppinglist.service;

import io.javalin.http.Context;
import io.javalin.http.Header;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.AuthenticationRepository;
import no.eirikgg.shoppinglist.db.repository.UserRepository;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class AuthenticationService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private UserRepository userRepository;
    private AuthenticationRepository authenticationRepository;


    public AuthenticationService(Db db){
        this.userRepository = new UserRepository(db);
        this.authenticationRepository = new AuthenticationRepository(db);
    }


    public Optional<UUID> login(String username, String password) throws InvalidSessionException {
        User user = userRepository.getUser(username).orElse(null);

        if (null == user) {
            log.debug("User with username \"{}\" not found", username);
            return Optional.empty();
        }

        String savedPasswordHash = user.getAuthorization().getPasswordHash();
        boolean correctPassword = PasswordService.verify(password, savedPasswordHash);
        if (!correctPassword) {
            return Optional.empty();
        }

        try {
            UUID sessionId = authenticationRepository.createSession(user);
            return Optional.of(sessionId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Optional<User> getLoggedInUser(UUID sessionId) throws InvalidSessionException, UserEmailNotVerifiedException {
        authenticationRepository.isValidSessionId(sessionId);

        return authenticationRepository.getUserFromSession(sessionId);
    }

    public void isValidSessionId(UUID sessionId) throws InvalidSessionException, UserEmailNotVerifiedException {
        authenticationRepository.isValidSessionId(sessionId);
    }



    public static Optional<UUID> getSessionIdFromRequest(Context ctx) {
        return Optional.ofNullable(ctx)
                .map(context -> context.header(Header.AUTHORIZATION))
                .map(header -> header.replace("Bearer ", ""))
                .map(UUID::fromString);
    }
}
