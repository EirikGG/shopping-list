package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.repository.UserRepository;
import no.eirikgg.shoppinglist.errors.EmailAlreadyVerifiedException;
import no.eirikgg.shoppinglist.errors.NoEmailConnectedToVerificationUuidException;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;

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
