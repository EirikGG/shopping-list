package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.Hidden;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public class UserRepository {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private DbUtils dbUtils = new DbUtils();

    private final Random random;

    public UserRepository() {
        try {
            random = SecureRandom.getInstanceStrong();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }





    public Person createPerson(
            Connection conn,
            String firstname,
            String lastname
    ) throws SQLException {
        LocalDateTime created = LocalDateTime.now();
        int personId = dbUtils.insert(conn, """
            insert into person (
                firstname, 
                lastname, 
                created,
                deleted
            ) values(
                ?,
                ?,
                ?,
                ?
            )
            """,
            firstname,
            lastname,
            created,
            LocalDateTime.of(9999, 12, 31, 23, 59, 59)
        ).orElseThrow(
            () -> new RuntimeException("Failed to save person")
        );

        List<Person> persistedPersons = dbUtils.select(conn, """
            select
                person_id,
                firstname, 
                lastname
            from 
                person
            where 
                person_id       = ?
                and ?           between created and deleted
            """,
            new Object[] {personId, created.plusSeconds(1)},
            rs -> new Person(
                rs.getInt("person_id").orElseThrow(),
                rs.getString("firstname").orElse(""),
                rs.getString("lastname").orElse("")
            )
        );

        if (1 != persistedPersons.size()) {
            throw new RuntimeException("Failed to save person");
        }

        return persistedPersons.get(0);
    }

    public Authorization saveAutorization(
            Connection conn, Person persistedPerson, String email, String hashedPassword
    ) throws UsernameAlreadyExistsException, SQLException {

        int numberOfLogins = dbUtils.selectSingle(conn, """
            select count(*) as number_of_logins 
            from person_login 
            where email = ?
            """,
                email,
            rs -> rs.getInt("number_of_logins").orElse(0)
        ).orElse(0);

        if (0 < numberOfLogins) {
            String msg = "Cant have multiple logins with the same email \"%s\"".formatted(email);
            log.warn(msg, email);
            throw new UsernameAlreadyExistsException(msg);
        }

        LocalDateTime created = LocalDateTime.now();
        int personLoginId = dbUtils.insert(conn, """
            insert into person_login (
                person_id,
                email, 
                password, 
                created,
                deleted,
                verification_code,
                verified_at
            ) values(?, ?, ?, ?, ?, ?, ?)
            """,
            persistedPerson.getId().orElseThrow(),
            email,
            Hidden.param(hashedPassword),
            created,
            LocalDateTime.of(9999, 12, 31, 23, 59),
            random.nextInt(10_000, 100_000),
            null
        ).orElseThrow(
            () -> new RuntimeException("Failed to save autorization")
        );


        List<Authorization> persistedAuthorization = dbUtils.select(conn, """
            select
                email, 
                password
            from 
                person_login
            where 
                person_login_id = ?
                and ? between created and deleted
            """,
            new Object[] {personLoginId, created.plusSeconds(1)},
            rs -> new Authorization(
                rs.getString("email").orElseThrow(),
                rs.getString("password").orElseThrow()
        ));

        if (1 != persistedAuthorization.size()) {
            throw new RuntimeException("Failed to save authorization");
        }

        return persistedAuthorization.get(0);
    }

    public Optional<User> getUser(Connection conn, String email) {
        List<User> users = dbUtils.select(conn, """
            select
                p.person_id                 person_id,
                p.firstname                 person_firstname,
                p.lastname                  person_lastname,
                login.person_login_id       person_login_id,
                login.email                 login_email,
                login.password              login_password
            from
                person                      as p
            join
                person_login                as login
                on login.person_id          = p.person_id
            where
                login.email                 = ?
            """,
            email,
            rs -> {
                Person person = new Person(
                    rs.getInt("person_id").orElseThrow(),
                    rs.getString("person_firstname").orElseThrow(),
                    rs.getString("person_lastname").orElseThrow()
                );
                Authorization authorization = new Authorization(
                    rs.getInt("person_login_id").orElseThrow(),
                    rs.getString("login_email").orElseThrow(),
                    rs.getString("login_password").orElseThrow()
                );

                return new User(person, authorization);
            }
        );

        if (1 < users.size()) {
            throw new RuntimeException("Found more than one user with email: " + email);
        }

        if (users.isEmpty()) {
            return Optional.empty();
        }
        else {
            return Optional.of(users.get(0));
        }
    }



    public record EmailSelect(String email, Optional<LocalDateTime> verifiedAt, Integer verificationCode) {}
    public Optional<EmailSelect> getEmail(Connection conn, String email) {
        return dbUtils.selectSingle(conn, """
            select email, verified_at, verification_code 
            from person_login 
            where email = ?
            """,
            email,
            rs -> new EmailSelect(
                rs.getString("email").orElseThrow(),
                rs.getLocalDateTime("verified_at"),
                rs.getInt("verification_code").orElseThrow()
            )
        );
    }


    public String verifyEmailAddress(Connection conn, String email) throws SQLException {
        int updatedRows = dbUtils.update(conn,"""
            update person_login 
            set verified_at = ?
            where email = ? 
            """,
            LocalDateTime.now(),
            email
        ).orElse(-1);

        if  (1 != updatedRows) {
            throw new RuntimeException("Updated " + updatedRows + " rows - expected one");
        }

        return email;
    }

    public int getEmailVerificationCode(Connection conn, User user) throws EmailNotRegisteredException {
        String email = user.getAuthorization().getEmail();

        return dbUtils.selectSingle(conn,
            "select verification_code from person_login where email = ?",
                email,
                rs -> rs.getInt("verification_code").orElseThrow()
        ).orElseThrow(
                () -> new EmailNotRegisteredException(email, "")
        );
    }
}
