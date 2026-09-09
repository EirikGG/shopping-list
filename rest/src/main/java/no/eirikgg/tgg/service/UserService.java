package no.eirikgg.tgg.service;

import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.db.user.User;
import no.eirikgg.tgg.db.user.UserRepository;

import java.sql.SQLException;

public class UserService {

    private UserRepository userRepository;


    public UserService(Db db){
        this.userRepository = new UserRepository(db);
    }


    public User persistUser(User newUser) {
        try {
            return userRepository.createUser(newUser);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to create new user", e);
        }
    }
}
