package no.eirikgg.tgg.db.user;

import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.service.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserRepository {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;

    public UserRepository(Db db){
        this.db = db;
    }


    public User createUser(User user) throws SQLException {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);
            Person person = savePerson(conn, user.getPerson());
            Authorization authorization = saveAutorization(conn, person, user.getAuthorization());
            Email email = saveEmail(conn, person, user.getEmail());
            PhoneNumber phoneNumber = savePhonenumber(conn, person, user.getPhoneNumber());

            conn.commit();

            return new User(person, authorization, email, phoneNumber);
        }
        catch (SQLException e) {
            conn.rollback();
            throw e;
        }
        finally {
            if (!conn.isClosed()) {
                conn.close();
            }
        }
    }



    private Person savePerson(Connection conn, Person person) {
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

    private Authorization saveAutorization(Connection conn, Person persistedPerson, Authorization authorization) {
        String password = authorization.getPasswordHash();
        String hashedPassword = PasswordService.hashPassword(password);

        LocalDateTime created = LocalDateTime.now();
        int personLoginId = db.insert(conn, """
            insert into person_login (
                person_id,
                username, 
                password, 
                created,
                deleted
            ) values(
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            persistedPerson.getId().orElseThrow(),
            authorization.getUsername(),
            hashedPassword,
                created,
            LocalDateTime.of(9999, 12, 31, 23, 59)
        ).orElseThrow(
            () -> new RuntimeException("Failed to save autorization")
        );


        List<Authorization> persistedAuthorization = db.select(conn, """
            select
                username, 
                password
            from 
                person_login
            where 
                person_login_id = ?
                and ? between created and deleted
            """,
            new Object[] {personLoginId, created.plusSeconds(1)},
            rs -> new Authorization(
                rs.getString("username").orElseThrow(),
                rs.getString("password").orElseThrow()
        ));

        persistedAuthorization.forEach(System.out::println);

        if (1 != persistedAuthorization.size()) {
            throw new RuntimeException("Failed to save authorization");
        }

        return persistedAuthorization.get(0);
    }

    private Email saveEmail(Connection conn, Person persistedPerson, Email email) {
        int personEmailId = db.insert(conn, """
            insert into person_email (
                person_id,
                email, 
                created,
                deleted
            ) values(
                ?,
                ?,
                ?,
                ?
            )
            """,
            persistedPerson.getId().orElseThrow(),
            email.getEmail(),
            LocalDateTime.now(),
            LocalDateTime.of(9999, 12, 31, 23, 59)
        ).orElseThrow(
            () -> new RuntimeException("Failed to save autorization")
        );

        List<Email> emails = db.select(conn, """
            select
                email
            from 
                person_email
            where 
                person_email_id = ?
            """,
            personEmailId,
            rs -> new Email(rs.getString("email").orElseThrow())
        );

        if (1 != emails.size()) {
            throw new RuntimeException("Failed to save email");
        }

        return emails.get(0);
    }

    private PhoneNumber savePhonenumber(Connection conn, Person persistedPerson, PhoneNumber phoneNumber) {
        int personPhoneNumberId = db.insert(conn, """
            insert into person_phone_number (
                person_id,
                country_code,
                phone_number,
                created,
                deleted
            ) values(
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            persistedPerson.getId().orElseThrow(),
            phoneNumber.getCountryCode(),
            phoneNumber.getPhoneNumber(),
            LocalDateTime.now(),
            LocalDateTime.of(9999, 12, 31, 23, 59)
        ).orElseThrow(
            () -> new RuntimeException("Failed to save phone number")
        );

        List<PhoneNumber> phoneNumbers = db.select(conn, """
            select
                country_code,
                phone_number
            from 
                person_phone_number
            where 
                person_phone_number_id = ?
            """,
            personPhoneNumberId,
            rs -> new PhoneNumber(
                    rs.getString("country_code").orElseThrow(),
                    rs.getString("phone_number").orElseThrow()
            )
        );

        if (1 != phoneNumbers.size()) {
            throw new RuntimeException("Failed to save phone number");
        }

        return phoneNumbers.get(0);
    }

    public Optional<User> getUser(String username) {
        try (Connection conn = db.getConnection()) {
            List<User> users = db.select(conn, """
                select
                    p.person_id                 person_id,
                    p.firstname                 person_firstname,
                    p.lastname                  person_lastname,
                    p.birthdate                 person_birthdate,
                    p.gender                    person_gender,
                    email.email                 contact_email,
                    phone.country_code          contact_country_code,
                    phone.phone_number          contact_phone_number,
                    login.person_login_id       person_login_id,
                    login.username              login_username,
                    login.password              login_password
                from
                    person                      as p
                join
                    person_email                as email
                    on email.person_id          = p.person_id
                join 
                    person_phone_number         as phone
                    on phone.person_id          = p.person_id
                join
                    person_login                as login
                    on login.person_id          = p.person_id
                where
                    login.username              = ?
                """,
                username,
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
                        rs.getString("login_username").orElseThrow(),
                        rs.getString("login_password").orElseThrow()
                    );
                    Email email = new Email(
                        rs.getString("contact_email").orElseThrow()
                    );
                    PhoneNumber phoneNumber = new PhoneNumber(
                        rs.getString("contact_country_code").orElseThrow(),
                        rs.getString("contact_phone_number").orElseThrow()
                    );

                    return new User(person, authorization, email, phoneNumber);
                }
            );

            if (1 < users.size()) {
                throw new RuntimeException("Failed to save user");
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

    public UUID createSession(User user) throws SQLException {
        try (Connection conn = db.getConnection()) {
            UUID sessionId = UUID.randomUUID();
            db.insert(conn, """
                insert into session(
                    session_id,
                    person_login_id,
                    valid_from,
                    valid_until
                ) values(
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                sessionId,
                user.getAuthorization().getId().orElseThrow(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(4)
            );

            return sessionId;
        }
    }

    public Optional<User> getUserFromSession(UUID sessionId) {
        try (Connection conn = db.getConnection()) {
            List<User> users = db.select(conn, """
                select
                    p.person_id                 person_id,
                    p.firstname                 person_firstname,
                    p.lastname                  person_lastname,
                    p.birthdate                 person_birthdate,
                    p.gender                    person_gender,
                    email.email                 contact_email,
                    phone.country_code          contact_country_code,
                    phone.phone_number          contact_phone_number,
                    login.username              login_username,
                    login.password              login_password
                from
                    person                      as p
                join
                    person_email                as email
                    on email.person_id          = p.person_id
                join 
                    person_phone_number         as phone
                    on phone.person_id          = p.person_id
                join
                    person_login                as login
                    on login.person_id          = p.person_id
                join 
                    session                     as s
                    on s.person_login_id        = login.person_login_id 
                where
                    s.session_id                = ?
                """,
                sessionId,
                rs -> {
                    Person person = new Person(
                        rs.getInt("person_id").orElseThrow(),
                        rs.getString("person_firstname").orElseThrow(),
                        rs.getString("person_lastname").orElseThrow(),
                        rs.getLocalDate("person_birthdate").orElseThrow(),
                        Person.Gender.valueOf(rs.getString("person_gender").orElseThrow())
                    );
                    Authorization authorization = new Authorization(
                        rs.getString("login_username").orElseThrow(),
                        rs.getString("login_password").orElseThrow()
                    );
                    Email email = new Email(
                        rs.getString("contact_email").orElseThrow()
                    );
                    PhoneNumber phoneNumber = new PhoneNumber(
                        rs.getString("contact_country_code").orElseThrow(),
                        rs.getString("contact_phone_number").orElseThrow()
                    );

                    return new User(person, authorization, email, phoneNumber);
                }
            );

            if (1 < users.size()) {
                throw new RuntimeException("Failed to save user");
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

    public boolean isValidSessionId(UUID sessionId) {
        try (Connection conn = db.getConnection()) {
            List<String> sessions = db.select(conn, """
                select
                    s.session_id        session_id
                from
                    session             as s
                where
                    s.session_id        = ?
                    and ?               between s.valid_from and s.valid_until 
                """,
                new Object[] {sessionId, LocalDateTime.now()},
                rs -> rs.getString("session_id").orElseThrow()
            );

            return !sessions.isEmpty();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
