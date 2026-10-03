package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.Hidden;
import no.eirikgg.shoppinglist.errors.EmailAlreadyVerifiedException;
import no.eirikgg.shoppinglist.errors.EmailNotRegisteredException;
import no.eirikgg.shoppinglist.errors.UsernameAlreadyExistsException;
import no.eirikgg.shoppinglist.errors.WrongEmailVerificationCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class UserRepository {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;

    private final Random random;

    public UserRepository(Db db) {
        this.db = db;

        try {
            random = SecureRandom.getInstanceStrong();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    public User createUser(User user) throws SQLException, UsernameAlreadyExistsException {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);
            Person person = savePerson(conn, user.getPerson());
            Authorization authorization = saveAutorization(conn, person, user.getAuthorization());

            conn.commit();

            return new User(person, authorization);
        }
        catch (Exception e) {
            conn.rollback();
            throw e;
        }
        finally {
            if (!conn.isClosed()) {
                conn.close();
            }
        }
    }



    private Person savePerson(Connection conn, Person person) throws SQLException {
        LocalDateTime created = LocalDateTime.now();
        int personId = db.insert(conn, """
            insert into person (
                firstname, 
                lastname, 
                birthdate,
                gender,
                created,
                deleted
            ) values(
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            person.getFirstName(),
            person.getLastName(),
            person.getBirthdate(),
            person.getGender().name(),
            created,
            LocalDateTime.of(9999, 12, 31, 23, 59, 59)
        ).orElseThrow(
            () -> new RuntimeException("Failed to save person")
        );

        List<Person> persistedPersons = db.select(conn, """
            select
                person_id,
                firstname, 
                lastname, 
                birthdate,
                gender
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
                rs.getString("lastname").orElse(""),
                rs.getLocalDate("birthdate").orElse(null),
                Person.Gender.valueOf(rs.getString("gender").orElse(""))
            )
        );

        if (1 != persistedPersons.size()) {
            throw new RuntimeException("Failed to save person");
        }

        return persistedPersons.get(0);
    }

    private Authorization saveAutorization(
            Connection conn, Person persistedPerson, Authorization authorization
    ) throws UsernameAlreadyExistsException, SQLException {

        String email = authorization.getEmail();
        int numberOfLogins = db.selectSingle(conn, """
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
        int personLoginId = db.insert(conn, """
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
            Hidden.param(authorization.getPasswordHash()),
            created,
            LocalDateTime.of(9999, 12, 31, 23, 59),
            random.nextInt(10_000, 100_000),
            null
        ).orElseThrow(
            () -> new RuntimeException("Failed to save autorization")
        );


        List<Authorization> persistedAuthorization = db.select(conn, """
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

    public Optional<User> getUser(String email) {
        try (Connection conn = db.getConnection()) {
            List<User> users = db.select(conn, """
                select
                    p.person_id                 person_id,
                    p.firstname                 person_firstname,
                    p.lastname                  person_lastname,
                    p.birthdate                 person_birthdate,
                    p.gender                    person_gender,
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
                        rs.getString("person_lastname").orElseThrow(),
                        rs.getLocalDate("person_birthdate").orElseThrow(),
                        Person.Gender.valueOf(rs.getString("person_gender").orElseThrow())
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
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isUsernameAvailable(String email) throws SQLException {
        try (Connection conn = db.getConnection()) {
            int numberOfUsernameEntries = db.selectSingle(conn,
                "select count(*) as value1 from person_login where email = ?",
                    email,
                    rs -> rs.getInt("value1").orElse(0)
            ).orElse(0);

            return 1 > numberOfUsernameEntries;
        }
    }



    public String verifyEmailAddress(
            String email, int verificationCode
    ) throws SQLException, EmailNotRegisteredException, EmailAlreadyVerifiedException, WrongEmailVerificationCodeException {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);

            record EmailSelect(String email, LocalDateTime verifiedAt, Integer verificationCode) {}
            EmailSelect emailSelect = db.selectSingle(conn, """
                    select email, verified_at, verification_code 
                    from person_login 
                    where email = ?
                    """,
                    email,
                    rs -> new EmailSelect(
                            rs.getString("email").orElse(null),
                            rs.getLocalDateTime("verified_at").orElse(null),
                            rs.getInt("verification_code").orElse(null)
                    )
            ).orElse(null);

            if (null == emailSelect || null == emailSelect.email()) {
                throw new EmailNotRegisteredException(email, "No user registrerd with email " + email);
            }

            LocalDateTime verifiedAt = emailSelect.verifiedAt();
            if (verifiedAt != null && verifiedAt.isBefore(LocalDateTime.now())) {
                throw new EmailAlreadyVerifiedException(email, "Email already verified");
            }

            if (!emailSelect.verificationCode().equals(verificationCode)) {
                throw new WrongEmailVerificationCodeException(email, verificationCode, "Wrong email verification code");
            }

            int updatedRows = db.update(conn,"""
                    update person_login 
                    set verified_at = ?
                    where email = ? 
                      and verification_code = ?
                    """,
                    LocalDateTime.now(),
                    email,
                    verificationCode
            ).orElse(-1);

            if  (1 != updatedRows) {
                throw new RuntimeException("Updated " + updatedRows + " rows - expected one");
            }

            conn.commit();
            return email;
        }
        catch (Exception e) {
            conn.rollback();
            throw e;
        }
        finally {
            if (!conn.isClosed()) {
                conn.close();
            }
        }
    }

    public int getEmailVerificationCode(User user) throws EmailNotRegisteredException {
        try (Connection conn = db.getConnection()) {
            String email = user.getAuthorization().getEmail();

            return db.selectSingle(conn,
                "select verification_code from person_login where email = ?",
                    email,
                    rs -> rs.getInt("verification_code").orElseThrow()
            ).orElseThrow(
                    () -> new EmailNotRegisteredException(email, "")
            );
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
