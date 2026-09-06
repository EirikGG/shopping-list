package no.eirikgg.data.sqllite.service;

import no.eirikgg.data.sqllite.db.Db;
import no.eirikgg.data.sqllite.db.user.Authorization;
import no.eirikgg.data.sqllite.db.user.User;
import no.eirikgg.data.sqllite.db.user.UserRepository;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class UserService {

    private UserRepository userRepository;


    public UserService(Db db){
        this.userRepository = new UserRepository(db);
    }


    public User createUser(User newUser) {
        try {
            return userRepository.createUser(newUser);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to create new user", e);
        }
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
            UUID sessionId = userRepository.createSession(user);
            boolean isValidSession = userRepository.isValidSessionId(sessionId);
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
        boolean isValidSession = userRepository.isValidSessionId(sessionId);
        if (!isValidSession) {
            return Optional.empty();
        }

        return userRepository.getUserFromSession(sessionId);
    }
}
