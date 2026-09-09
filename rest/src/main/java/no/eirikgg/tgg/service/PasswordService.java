package no.eirikgg.tgg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public class PasswordService {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String STORAGE_NAME = "pbkdf2-sha256";

    private static final int ITERATIONS = 600_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordService() {
    }

    public static String hash(char[] password) {
        Objects.requireNonNull(password, "password");

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);

        byte[] hash = deriveHash(
                password,
                salt,
                ITERATIONS,
                HASH_LENGTH_BYTES
        );

        return String.join(
                "$",
                STORAGE_NAME,
                Integer.toString(ITERATIONS),
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash)
        );
    }

public static boolean verify(
        String plainTextPassword,
        String passwordHash
    ) {
        Objects.requireNonNull(plainTextPassword, "plainTextPassword");

        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }

        char[] passwordChars = plainTextPassword.toCharArray();

        try {
            String[] parts = passwordHash.split("\\$", -1);

            if (parts.length != 4) {
                return false;
            }

            if (!STORAGE_NAME.equals(parts[0])) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);

            if (iterations <= 0 || iterations > 10_000_000) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);

            byte[] actualHash = deriveHash(
                    passwordChars,
                    salt,
                    iterations,
                    expectedHash.length
            );

            return MessageDigest.isEqual(expectedHash, actualHash);

        }
        catch (IllegalArgumentException e) {
            return false;
        }
        finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private static byte[] deriveHash(
            char[] password,
            byte[] salt,
            int iterations,
            int hashLengthBytes
    ) {
        PBEKeySpec specification = new PBEKeySpec(
            password,
            salt,
            iterations,
            hashLengthBytes * 8
        );

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(specification).getEncoded();
        }
        catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        }
        finally {
            specification.clearPassword();
        }
    }


private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    public static String hashPassword(String password) {
        char[] passwordBytes = password.toCharArray();
        String hash = PasswordService.hash(passwordBytes);

        return hash;
    }
}