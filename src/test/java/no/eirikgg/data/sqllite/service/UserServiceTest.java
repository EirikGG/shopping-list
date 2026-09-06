package no.eirikgg.data.sqllite.service;

import no.eirikgg.data.sqllite.db.InMemoryH2Db;
import no.eirikgg.data.sqllite.db.user.Authorization;
import no.eirikgg.data.sqllite.db.user.User;
import no.eirikgg.data.sqllite.db.user.UserTestDataGenerator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    @Test
    void createUser() {
        UserService service = new UserService(InMemoryH2Db.createMigratedInstance());

        User newUser = UserTestDataGenerator.getUser();
        User persisted = service.createUser(newUser);


        assertNotNull(persisted.getPerson().getId().orElse(null));
        assertEquals(newUser.getPerson().getFirstName(), persisted.getPerson().getFirstName());
        assertEquals(newUser.getPerson().getLastName(), persisted.getPerson().getLastName());
        assertEquals(newUser.getPerson().getBirthdate(), persisted.getPerson().getBirthdate());
        assertEquals(newUser.getPerson().getGender(), persisted.getPerson().getGender());

        assertEquals(newUser.getAuthorization().getUsername(), persisted.getAuthorization().getUsername());
        assertTrue(PasswordService.verify(
            newUser.getAuthorization().getPasswordHash(),
            persisted.getAuthorization().getPasswordHash())
        ) ;
        assertFalse(PasswordService.verify(
            "Passord test",
            persisted.getAuthorization().getPasswordHash())
        );

        assertEquals(newUser.getEmail().getEmail(), persisted.getEmail().getEmail());

        assertEquals(newUser.getPhoneNumber().getCountryCode(), persisted.getPhoneNumber().getCountryCode());
        assertEquals(newUser.getPhoneNumber().getPhoneNumber(), persisted.getPhoneNumber().getPhoneNumber());
    }



    @Test
    void login() {
        UserService service = new UserService(InMemoryH2Db.createMigratedInstance());

        User newUser = UserTestDataGenerator.getUser();
        User persisted = service.createUser(newUser);

        Authorization authorization = newUser.getAuthorization();
        UUID sessionId = service.login(authorization).orElse(null);

        assertNotNull(sessionId);


        User getLoggedInUser = service.getLoggedInUser(sessionId).orElse(null);
        assertEquals(persisted.getPerson().getId().orElse(-1), getLoggedInUser.getPerson().getId().orElse(-2));
        assertEquals(newUser.getPerson().getFirstName(), getLoggedInUser.getPerson().getFirstName());
        assertEquals(newUser.getPerson().getLastName(), getLoggedInUser.getPerson().getLastName());
        assertEquals(newUser.getPerson().getBirthdate(), getLoggedInUser.getPerson().getBirthdate());
        assertEquals(newUser.getPerson().getGender(), getLoggedInUser.getPerson().getGender());





        Authorization notOkAuthorization = new Authorization(
                authorization.getUsername(),
                "Not correct password"
        );

        UUID notOkSessionId = service.login(notOkAuthorization).orElse(null);
        assertNull(notOkSessionId);
        assertNull(service.getLoggedInUser(notOkSessionId).orElse(null));



    }
}