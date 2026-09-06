package no.eirikgg.data.sqllite;

import no.eirikgg.data.sqllite.db.Db;
import no.eirikgg.data.sqllite.db.H2Db;
import no.eirikgg.data.sqllite.db.LiquibaseMigrator;
import no.eirikgg.data.sqllite.db.user.Authorization;
import no.eirikgg.data.sqllite.db.user.Email;
import no.eirikgg.data.sqllite.db.user.Person;
import no.eirikgg.data.sqllite.db.user.PhoneNumber;
import no.eirikgg.data.sqllite.db.user.User;
import no.eirikgg.data.sqllite.service.PasswordService;
import no.eirikgg.data.sqllite.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.UUID;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final LiquibaseMigrator migrator = new LiquibaseMigrator();


    public static void main(String[] args) {
        Db db = new H2Db("jdbc:h2:file:C:/Users/Eirik/Projects/tgg-website/dev-db");
        migrator.migrate(db);

        UserService userService = new UserService(db);


        Person person = new Person(
                "Eirik",
                "Gribbestad Gustafsson",
                LocalDate.of(1994, 05,14),
                Person.Gender.MALE
        );

        Email email = new Email(
                "eirik@systor.st"
        );

        PhoneNumber phoneNumber = new PhoneNumber(
                "NO",
                "+4799489816"
        );

        String username = "eirik" + System.currentTimeMillis() + "@systor.st";
        Authorization authorization = new Authorization(
                username,
                PasswordService.hashPassword("Passord1234")
        );

        User user = new User(person, authorization, email, phoneNumber);
        userService.createUser(user);


        UUID sessionId = userService.login(user.getAuthorization()).orElse(null);
        log.info("Login successful - sessionId: {}", sessionId);

    }
}
