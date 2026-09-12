package no.eirikgg.tgg.db.repository;

import no.eirikgg.tgg.datamodel.Authorization;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.datamodel.Email;
import no.eirikgg.tgg.datamodel.Person;
import no.eirikgg.tgg.datamodel.PhoneNumber;
import no.eirikgg.tgg.datamodel.User;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AuthenticationRepository {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;

    public AuthenticationRepository(Db db){
        this.db = db;
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
