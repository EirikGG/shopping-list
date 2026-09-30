package no.eirikgg.shoppinglist.db.user;

import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.datamodel.Email;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.PhoneNumber;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.service.PasswordService;

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