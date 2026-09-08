package no.eirikgg.tgg.service;

import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.db.authentication.AuthenticationRepository;
import no.eirikgg.tgg.db.user.Authorization;
import no.eirikgg.tgg.db.user.User;
import no.eirikgg.tgg.db.user.UserRepository;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class AuthenticationService {

    private UserRepository userRepository;
    private AuthenticationRepository authenticationRepository;


    public AuthenticationService(Db db){
        this.userRepository = new UserRepository(db);
        this.authenticationRepository = new AuthenticationRepository(db);
    }


    public Optional<UUID> login(Authorization auth) {
        String username = auth.getUsername();
        String hashedPassword = auth.getPasswordHash();

        User user = userRepository.getUser(username).orElse(null);

        if (null == user) {
            return Optional.empty();
        }

        String savedPasswordHash = user.getAuthorization().getPasswordHash();
        boolean correctPassword = PasswordService.verify(hashedPassword, savedPasswordHash);
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
}
