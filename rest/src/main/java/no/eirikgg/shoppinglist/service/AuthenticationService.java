package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Session;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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


    // The new session, and who it belongs to, for the login token.
    public record Login(UUID sessionId, int personId, Instant expiresAt) {}

    public Optional<Login> login(String username, String password) throws InvalidSessionException {
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

            AuthenticationRepository.NewSession session = authenticationRepository.createSession(conn, user);
            return Optional.of(new Login(
                    session.sessionId(),
                    user.getPerson().getId().orElseThrow(),
                    session.validUntil().atZone(ZoneId.systemDefault()).toInstant()
            ));
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

    public void logout(Session session) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            authenticationRepository.invalidateSession(conn, sessionId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
