package no.eirikgg.shoppinglist.db.user;

import no.eirikgg.shoppinglist.datamodel.Authorization;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.service.PasswordService;

public class UserTestDataGenerator {


    public static User getUser() {
        User user = new User(
            getPersonInformation(),
            getAuthorization()
        );

        return user;
    }

    private static Authorization getAuthorization() {
        return new Authorization(
            "unit-test-username",
            PasswordService.hashPassword("unit-test-password")
        );
    }

    private static Person getPersonInformation() {
        return new Person("Ola", "Normann");
    }
}