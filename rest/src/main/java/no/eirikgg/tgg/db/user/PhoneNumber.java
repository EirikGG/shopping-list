package no.eirikgg.tgg.db.user;

public class PhoneNumber {

    private String countryCode;
    private String phoneNumber;


    public PhoneNumber(String countryCode, String phoneNumber) {
        this.countryCode = countryCode;
        this.phoneNumber = phoneNumber;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}
