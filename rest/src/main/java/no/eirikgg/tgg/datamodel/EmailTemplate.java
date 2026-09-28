package no.eirikgg.tgg.datamodel;

public class EmailTemplate {
    public static class NewUser {
        public static String getSubject(String firstName, String lastName) {
            return "Verify email for new user " + firstName + " " + lastName;
        }

        public static String getBody(String verificationUrl) {
            return """
                <h2>Verify your email address</h2>
        
                <p>
                    Thanks for creating an account.
                    Click the link below to verify your email address.
                </p>
        
                <p>
                    <a href="%s">Verify email address</a>
                </p>
        
                <p>
                    If the link doesn't work, copy and paste this URL into your browser:
                </p>
        
                <p>
                    <a href="%s">%s</a>
                </p>
        
                <p>
                    If you did not create an account, you can ignore this email.
                </p>
                """.formatted(
                    verificationUrl,
                    verificationUrl,
                    verificationUrl
                );
        }
    }
}
