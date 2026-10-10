package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.AuthenticationRepository;
import no.eirikgg.shoppinglist.db.repository.UserRepository;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class AuthenticationService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;
    private UserRepository userRepository = new UserRepository();
    private AuthenticationRepository authenticationRepository = new AuthenticationRepository();


    public AuthenticationService(Db db){
        this.db = db;
    }


    public Optional<UUID> login(String username, String password) throws InvalidSessionException {
        try (Connection conn = db.getConnection()) {
            User user = userRepository.getUser(conn, username).orElse(null);

            if (null == user) {
                log.debug("User with username \"{}\" not found", username);
                return Optional.empty();
            }

            String savedPasswordHash = user.getAuthorization().getPasswordHash();
            boolean correctPassword = PasswordService.verify(password, savedPasswordHash);
            if (!correctPassword) {
                return Optional.empty();
            }

            UUID sessionId = authenticationRepository.createSession(conn, user);
            return Optional.of(sessionId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Optional<User> getLoggedInUser(UUID sessionId)
            throws InvalidSessionException, UserEmailNotVerifiedException {
        validateSession(sessionId);

        try (Connection conn = db.getConnection()) {
            return authenticationRepository.getUserFromSession(conn, sessionId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void validateSession(UUID sessionId) throws InvalidSessionException, UserEmailNotVerifiedException {
        try (Connection conn = db.getConnection()) {
            AuthenticationRepository.SessionRow sessionRow = authenticationRepository
                .getSessionRow(conn, sessionId)
                .orElseThrow(() -> new InvalidSessionException(sessionId, "No active session found"));

            LocalDateTime verifiedAt = sessionRow.verifiedAt().orElse(LocalDateTime.MAX);
            String email = sessionRow.email();
            if (verifiedAt.isAfter(LocalDateTime.now())) {
                throw new UserEmailNotVerifiedException(email, "User email is not verified");
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void logout(UUID sessionId) {
        try (Connection conn = db.getConnection()) {
            authenticationRepository.invalidateSession(conn, sessionId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
