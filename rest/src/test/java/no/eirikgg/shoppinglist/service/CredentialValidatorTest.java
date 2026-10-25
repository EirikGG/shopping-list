package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.errors.InvalidEmailException;
import no.eirikgg.shoppinglist.errors.WeakPasswordException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ola@example.com",
            "ola.normann+shopping@mail.example.no",
            "o'brien@example.co.uk",
            "x@sub-domain.example.org"
    })
    void validEmails(String email) {
        assertDoesNotThrow(() -> CredentialValidator.validateEmail(email));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "plainaddress",
            "@example.com",
            "ola@",
            "ola@example",
            "ola@example.c",
            "ola@@example.com",
            "ola..normann@example.com",
            ".ola@example.com",
            "ola.@example.com",
            "ola normann@example.com",
            "ola@-example.com",
            "ola@example-.com",
            "ola@example..com",
            "ola@example.123"
    })
    void invalidEmails(String email) {
        assertThrows(InvalidEmailException.class, () -> CredentialValidator.validateEmail(email));
    }

    @Test
    void email_isTrimmed_andLengthIsLimited() throws InvalidEmailException {
        assertEquals("ola@example.com", CredentialValidator.validateEmail("  ola@example.com "));

        String longLocal = "a".repeat(65) + "@example.com";
        assertThrows(InvalidEmailException.class, () -> CredentialValidator.validateEmail(longLocal));
        // Over the limit of 254 characters.
        String longDomain = "ola@" + "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "."
                + "d".repeat(57) + ".com";
        assertThrows(InvalidEmailException.class, () -> CredentialValidator.validateEmail(longDomain));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test1234", "aB3defgh", "Correct Horse Battery 9"})
    void strongPasswords(String password) {
        assertDoesNotThrow(() -> CredentialValidator.validatePassword(password));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "Test123",      // too short
            "test1234",     // no uppercase
            "TEST1234",     // no lowercase
            "Testtesttest"  // no digit
    })
    void weakPasswords(String password) {
        assertThrows(WeakPasswordException.class, () -> CredentialValidator.validatePassword(password));
    }

    @Test
    void password_lengthIsLimited() {
        String tooLong = "Aa1" + "x".repeat(126);
        assertThrows(WeakPasswordException.class, () -> CredentialValidator.validatePassword(tooLong));
    }
}
