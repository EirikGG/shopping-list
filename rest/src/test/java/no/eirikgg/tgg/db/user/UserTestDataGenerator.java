package no.eirikgg.tgg.db.user;

import no.eirikgg.tgg.datamodel.Authorization;
import no.eirikgg.tgg.datamodel.Email;
import no.eirikgg.tgg.datamodel.Person;
import no.eirikgg.tgg.datamodel.PhoneNumber;
import no.eirikgg.tgg.datamodel.User;
import no.eirikgg.tgg.service.PasswordService;

import java.time.LocalDate;
import java.util.UUID;

public class UserTestDataGenerator {


    public static User getUser() {
        User user = new User(
            getPersonInformation(),
            getAuthorization(),
            getEmail(),
            getPhoneNumber()
        );

        return user;
    }


    private static Email getEmail() {
        return new Email(
            "unit-test@test.com",
                UUID.randomUUID()
        );
    }


    private static PhoneNumber getPhoneNumber() {
        return new PhoneNumber(
                "NO",
            "99489816"
        );
    }

    private static Authorization getAuthorization() {
        return new Authorization(
            "unit-test-username",
            PasswordService.hashPassword("unit-test-password")
        );
    }

    private static Person getPersonInformation() {
        return new Person("Ola", "Normann", LocalDate.of(1994, 05, 14), Person.Gender.MALE);
    }
}