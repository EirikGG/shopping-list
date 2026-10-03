package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.errors.InvalidSessionException;
import no.eirikgg.shoppinglist.errors.UserEmailNotVerifiedException;
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
                    login.email                 login_email,
                    login.password              login_password
                from
                    person                      as p
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
                        rs.getString("login_email").orElseThrow(),
                        rs.getString("login_password").orElseThrow()
                    );

                    return new User(person, authorization);
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

    public void isValidSessionId(UUID sessionId) throws InvalidSessionException, UserEmailNotVerifiedException {
        record Resultset(UUID sessionId, LocalDateTime verifiedAt, String email) {}
        try (Connection conn = db.getConnection()) {
             List<Resultset> sessions = db.select(conn, """
                select
                    session.session_id          as session_id,
                    login.email                 as email,
                    login.verified_at           as verified_at
                from
                    session                     as session
                join 
                    person_login                as login
                    on login.person_login_id    = session.person_login_id                                 
                where
                    session.session_id          = ?
                    and ?                       between session.valid_from and session.valid_until 
                """,
                new Object[] {sessionId, LocalDateTime.now()},
                rs -> new Resultset(
                    rs.getUuid("session_id").orElseThrow(),
                    rs.getLocalDateTime("verified_at").orElse(null),
                    rs.getString("email").orElseThrow()
                )
            );

             if (sessions.isEmpty()) {
                 throw new InvalidSessionException(sessionId, "No active session found");
             }

             if (1 < sessions.size()) {
                 throw new RuntimeException("To many sessions found");
             }

             Resultset resultset = sessions.get(0);
             LocalDateTime verifiedAt = resultset.verifiedAt();
             String email = resultset.email();
             if (null == verifiedAt || verifiedAt.isAfter(LocalDateTime.now())) {
                throw new UserEmailNotVerifiedException(email, "User email is not verified");
             }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
