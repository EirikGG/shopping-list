package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.errors.InvalidEmailException;
import no.eirikgg.shoppinglist.errors.WeakPasswordException;

import java.util.regex.Pattern;

// Checks the email and password of a new account. The web app checks the same
// rules (web/src/validation.js), so keep the two in step.
public final class CredentialValidator {

    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int EMAIL_LOCAL_MAX_LENGTH = 64;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;

    // local@domain.tld: dot-separated atoms before the @, and dot-separated
    // labels after it ending in a top-level domain of at least two letters.
    private static final String ATOM = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?";
    private static final Pattern EMAIL = Pattern.compile(
            "^" + ATOM + "(?:\\." + ATOM + ")*@(?:" + LABEL + "\\.)+[A-Za-z]{2,63}$"
    );

    private CredentialValidator() {}

    // Returns the email with surrounding whitespace removed.
    public static String validateEmail(String email) throws InvalidEmailException {
        String trimmed = null == email ? "" : email.trim();
        int at = trimmed.lastIndexOf('@');

        boolean valid = trimmed.length() <= EMAIL_MAX_LENGTH
                && at > 0
                && at <= EMAIL_LOCAL_MAX_LENGTH
                && EMAIL.matcher(trimmed).matches();
        if (!valid) {
            throw new InvalidEmailException("Not a valid email address");
        }
        return trimmed;
    }

    // At least 8 and at most 128 characters, with a lowercase letter, an
    // uppercase letter and a digit.
    public static void validatePassword(String password) throws WeakPasswordException {
        boolean valid = null != password
                && password.length() >= PASSWORD_MIN_LENGTH
                && password.length() <= PASSWORD_MAX_LENGTH
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isDigit);
        if (!valid) {
            throw new WeakPasswordException("Password does not meet the requirements");
        }
    }
}
