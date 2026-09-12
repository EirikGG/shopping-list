package no.eirikgg.tgg.service;

import io.javalin.http.Context;
import io.javalin.http.Header;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.db.repository.AuthenticationRepository;
import no.eirikgg.tgg.db.repository.UserRepository;
import no.eirikgg.tgg.datamodel.User;
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


    public Optional<UUID> login(String username, String password) {
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
            boolean isValidSession = authenticationRepository.isValidSessionId(sessionId);
            if (!isValidSession) {
                return Optional.empty();
            }

            return Optional.of(sessionId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Optional<User> getLoggedInUser(UUID sessionId) {
        boolean isValidSession = authenticationRepository.isValidSessionId(sessionId);
        if (!isValidSession) {
            return Optional.empty();
        }

        return authenticationRepository.getUserFromSession(sessionId);
    }

    public boolean isValidSessionId(UUID sessionId) {
        return authenticationRepository.isValidSessionId(sessionId);
    }



    public static Optional<UUID> getSessionIdFromRequest(Context ctx) {
        return Optional.ofNullable(ctx)
                .map(context -> context.header(Header.AUTHORIZATION))
                .map(header -> header.replace("Bearer ", ""))
                .map(UUID::fromString);
    }
}
