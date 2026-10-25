package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.repository.UserRepository;
import no.eirikgg.shoppinglist.errors.EmailAlreadyVerifiedException;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;
import no.eirikgg.shoppinglist.errors.WrongEmailVerificationCodeException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class UserService {

    private UserRepository userRepository = new UserRepository();

    private Db db;

    public UserService(Db db){
        this.db = db;
    }


    public User createUser(
            String firstname,
            String lastname,
            String email,
            String hashedPassword
    ) throws UsernameAlreadyExistsException {
        try (Connection conn = db.getConnection()) {
            try {
                conn.setAutoCommit(false);
                Person person = userRepository.createPerson(
                        conn,
                        firstname,
                        lastname
                );
                Authorization authorization = userRepository.saveAutorization(
                        conn,
                        person,
                        email,
                        hashedPassword
                );

                conn.commit();
                return new User(person, authorization);
            }
            catch (Throwable t) {
                conn.rollback();
                throw t;
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String verifyEmailAddress(String email, int verificationCode)
            throws EmailNotRegisteredException, EmailAlreadyVerifiedException, WrongEmailVerificationCodeException {
        try (Connection conn = db.getConnection()) {
            try {
                conn.setAutoCommit(false);

                UserRepository.EmailSelect emailInfo = userRepository.getEmail(conn, email).orElseThrow(
                    () -> new EmailNotRegisteredException(email, "No user registrerd with email " + email)
                );

                LocalDateTime verifiedAt = emailInfo.verifiedAt().orElse(LocalDateTime.MAX);
                if (verifiedAt.isBefore(LocalDateTime.now())) {
                    throw new EmailAlreadyVerifiedException(email, "Email already verified");
                }

                Integer verificationCodeActual = emailInfo.verificationCode();
                boolean isCorrectCode = verificationCodeActual.equals(verificationCode);
                if (!isCorrectCode) {
                    String errorMsg = "Wrong email verification code";
                    throw new WrongEmailVerificationCodeException(email, verificationCode, errorMsg);
                }

                userRepository.verifyEmailAddress(conn, email);
                conn.commit();
                return email;
            }
            catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
