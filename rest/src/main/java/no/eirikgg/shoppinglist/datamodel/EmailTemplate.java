package no.eirikgg.shoppinglist.datamodel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailTemplate {
    public static class NewUser {
        public static String getSubject(String firstName, String lastName) {
            return "Verify email for new user " + firstName + " " + lastName;
        }

        public static String getBody(int verificationNum) {
            return """
            <h2>Verify your email address</h2>
    
            <p>
                Thanks for creating an account.
                Use the verification code below to verify your email address.
            </p>
    
            <p style="font-size: 28px; font-weight: bold; letter-spacing: 4px;">
                %d
            </p>
    
            <p>
                Enter this code on the verification page to complete your registration.
            </p>
    
            <p>
                If you did not create an account, you can ignore this email.
            </p>
            """.formatted(verificationNum);
        }

        public static String getVerificationCodeFromBody(String body) {
            Pattern pattern = Pattern.compile(">\\s*(\\d{5})\\s*</p>");

            Matcher matcher = pattern.matcher(body);

            if (!matcher.find()) {
                throw new IllegalArgumentException("Verification code not found");
            }

            return matcher.group(1);
        }
    }
}
