package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AuthenticationRepository {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private DbUtils dbUtils = new DbUtils();

    // How long a login lasts, counted from logging in; it is not extended by
    // use. The login token expires at the same time.
    public static final Duration SESSION_LENGTH = Duration.ofDays(30);


    public record NewSession(UUID sessionId, LocalDateTime validUntil) {}

    public NewSession createSession(Connection conn, User user) throws SQLException {
        UUID sessionId = UUID.randomUUID();
        LocalDateTime validFrom = LocalDateTime.now();
        LocalDateTime validUntil = validFrom.plus(SESSION_LENGTH);
        dbUtils.insert(conn, """
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
            validFrom,
            validUntil
        );

        return new NewSession(sessionId, validUntil);
    }

    public Optional<User> getUserFromSession(Connection conn, UUID sessionId) {
        List<User> users = dbUtils.select(conn, """
            select
                p.person_id                 person_id,
                p.firstname                 person_firstname,
                p.lastname                  person_lastname,
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
                    rs.getString("person_lastname").orElseThrow()
                );
                Authorization authorization = new Authorization(
                    rs.getString("login_email").orElseThrow(),
                    rs.getString("login_password").orElseThrow()
                );

                return new User(person, authorization);
            }
        );

        if (1 < users.size()) {
            throw new RuntimeException("Failed to get user");
        }

        if (users.isEmpty()) {
            return Optional.empty();
        }
        else {
            return Optional.of(users.get(0));
        }
    }

    public void invalidateSession(Connection conn, UUID sessionId) throws SQLException {
        dbUtils.update(conn, """
                update session
                set valid_until = ?
                where session_id = ?
                """,
                LocalDateTime.now(),
                sessionId
        );
    }

    public record SessionRow(UUID sessionId, Optional<LocalDateTime> verifiedAt, String email) {}
    public Optional<SessionRow> getSessionRow(Connection conn, UUID sessionId) {
        List<SessionRow> sessions = dbUtils.select(conn, """
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
            rs -> new SessionRow(
                rs.getUuid("session_id").orElseThrow(),
                rs.getLocalDateTime("verified_at"),
                rs.getString("email").orElseThrow()
            )
        );

        if (sessions.isEmpty()) {
            return Optional.empty();
        }

        if (1 < sessions.size()) {
            throw new RuntimeException("To many sessions found");
        }

        return Optional.ofNullable(sessions.get(0));
    }
}
