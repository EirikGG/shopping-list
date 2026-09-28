package no.eirikgg.tgg.service;

import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.datamodel.User;
import no.eirikgg.tgg.db.repository.UserRepository;
import no.eirikgg.tgg.errors.EmailAlreadyVerifiedException;
import no.eirikgg.tgg.errors.NoEmailConnectedToVerificationUuidException;
import no.eirikgg.tgg.errors.UsernameAlreadyExistsException;

import java.sql.SQLException;
import java.util.UUID;

public class UserService {

    private UserRepository userRepository;


    public UserService(Db db){
        this.userRepository = new UserRepository(db);
    }


    public User persistUser(User newUser) throws UsernameAlreadyExistsException {
        try {
            return userRepository.createUser(newUser);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to create new user", e);
        }
    }

    public boolean isUsernameAvailable(String username) {
        try {
            return userRepository.isUsernameAvailable(username);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to check if username is available", e);
        }
    }

    public String verifyEmailAddressUsingUuid
            (UUID verificationUuid
    ) throws NoEmailConnectedToVerificationUuidException, EmailAlreadyVerifiedException {
        try {
            return userRepository.verifyEmailUsingVerificationUuid(verificationUuid);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to check if username is available", e);
        }
    }
}
