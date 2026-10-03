package no.eirikgg.shoppinglist.errors;

public class WrongEmailVerificationCodeException extends Exception {

    private String email;
    private int verificationCode;

    public WrongEmailVerificationCodeException(String email, int verificationCode, String message) {
        super(message);
        this.email = email;
        this.verificationCode = verificationCode;
    }

    public String getEmail() {
        return email;
    }

    public int getVerificationCode() {
        return verificationCode;
    }
}
