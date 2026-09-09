package no.eirikgg.tgg.datamodel;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PhoneNumber {


    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String countryCode;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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
