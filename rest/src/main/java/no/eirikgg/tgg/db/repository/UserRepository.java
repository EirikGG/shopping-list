package no.eirikgg.tgg.db.repository;

import no.eirikgg.tgg.datamodel.Authorization;
import no.eirikgg.tgg.datamodel.Email;
import no.eirikgg.tgg.datamodel.Person;
import no.eirikgg.tgg.datamodel.PhoneNumber;
import no.eirikgg.tgg.datamodel.User;
import no.eirikgg.tgg.db.Db;
import no.eirikgg.tgg.errors.EmailAlreadyVerifiedException;
import no.eirikgg.tgg.errors.NoEmailConnectedToVerificationUuidException;
import no.eirikgg.tgg.errors.UsernameAlreadyExistsException;
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


    public User createUser(User user) throws SQLException, UsernameAlreadyExistsException {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);
            Person person = savePerson(conn, user.getPerson());
            Authorization authorization = saveAutorization(conn, person, user.getAuthorization());
            Email email = saveEmail(conn, person, user.getEmail());
            PhoneNumber phoneNumber = savePhonenumber(conn, person, user.getPhonenumber());

            conn.commit();

            return new User(person, authorization, email, phoneNumber);
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

        String username = authorization.getUsername();
        int numberOfLogins = db.selectSingle(conn, """
            select count(*) as number_of_logins 
            from person_login 
            where username = ?
            """,
                username,
            rs -> rs.getInt("number_of_logins").orElse(0)
        ).orElse(0);

        if (0 < numberOfLogins) {
            String msg = "Cant have multiple logins with the same username \"%s\"".formatted(username);
            log.warn(msg, username);
            throw new UsernameAlreadyExistsException(msg);
        }

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
                username,
            authorization.getPasswordHash(),
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

        if (1 != persistedAuthorization.size()) {
            throw new RuntimeException("Failed to save authorization");
        }

        return persistedAuthorization.get(0);
    }

    private Email saveEmail(Connection conn, Person persistedPerson, Email email) throws SQLException {
        int personEmailId = db.insert(conn, """
            insert into person_email (
                person_id,
                email,
                verification_uuid,
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
            email.getEmail(),
            email.getVerificationUuid(),
            LocalDateTime.now(),
            LocalDateTime.of(9999, 12, 31, 23, 59)
        ).orElseThrow(
            () -> new RuntimeException("Failed to save autorization")
        );

        List<Email> emails = db.select(conn, """
            select
                email,
                verification_uuid
            from 
                person_email
            where 
                person_email_id = ?
            """,
            personEmailId,
            rs -> new Email(
                    rs.getString("email").orElseThrow(),
                    rs.getUuid("verification_uuid").orElseThrow()
            )
        );

        if (1 != emails.size()) {
            throw new RuntimeException("Failed to save email");
        }

        return emails.get(0);
    }

    private PhoneNumber savePhonenumber(Connection conn, Person persistedPerson, PhoneNumber phoneNumber) throws SQLException {
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
                    email.verification_uuid     verification_uuid,
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
                        rs.getString("contact_email").orElseThrow(),
                        rs.getUuid("verification_uuid").orElseThrow()
                    );
                    PhoneNumber phoneNumber = new PhoneNumber(
                        rs.getString("contact_country_code").orElseThrow(),
                        rs.getString("contact_phone_number").orElseThrow()
                    );

                    return new User(person, authorization, email, phoneNumber);
                }
            );

            if (1 < users.size()) {
                throw new RuntimeException("Found more than one user with username: " + username);
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

    public boolean isUsernameAvailable(String username) throws SQLException {
        try (Connection conn = db.getConnection()) {
            int numberOfUsernameEntries = db.selectSingle(conn,
                "select count(*) as value1 from person_login where username = ?",
                    username,
                    rs -> rs.getInt("value1").orElse(0)
            ).orElse(0);

            return 1 > numberOfUsernameEntries;
        }
    }



    public String verifyEmailUsingVerificationUuid(
            UUID verificationUuid
    ) throws SQLException, NoEmailConnectedToVerificationUuidException, EmailAlreadyVerifiedException {
        Connection conn = db.getConnection();
        try {
            conn.setAutoCommit(false);

            record EmailSelect(String email, LocalDateTime verified) {}
            EmailSelect emailSelect = db.selectSingle(conn,
                    "select email, verified from person_email where verification_uuid = ?",
                    verificationUuid,
                    rs -> new EmailSelect(
                            rs.getString("email").orElse(null),
                            rs.getLocalDateTime("verified").orElse(null)
                    )
            ).orElse(null);


            String email = emailSelect.email();
            LocalDateTime verified = emailSelect.verified();

            if (null == email) {
                throw new NoEmailConnectedToVerificationUuidException(verificationUuid,
                        "No email connected to verification uuid " + verificationUuid
                );
            }

            if (verified != null) {
                throw new EmailAlreadyVerifiedException(email, "Email already verified");
            }

            int updatedRows = db.update(conn,"""
                    update person_email 
                    set verified = ?
                    where verification_uuid = ?
                    """,
                    LocalDateTime.now(),
                    verificationUuid
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
}
