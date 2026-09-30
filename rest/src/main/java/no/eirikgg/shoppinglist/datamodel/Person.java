package no.eirikgg.shoppinglist.datamodel;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Person extends DbIdentity {

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String firstName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String lastName;
    @JsonProperty(value = "birthdate", access = JsonProperty.Access.READ_ONLY)
    private LocalDate birthdate;

    @JsonIgnore
    public static final DateTimeFormatter birthdateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Gender gender;

    public Person(Integer id, String firstName, String lastName, LocalDate birthdate, Gender gender) {
        super(id);
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthdate = birthdate;
        this.gender = gender;
    }

    public Person(String firstName, String lastName, LocalDate birthdate, Gender gender) {
        this(null, firstName, lastName, birthdate, gender);
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthdate() {
        return birthdate;
    }

    @JsonGetter("birthdate")
    public String getBirthdateAsText() {
        return birthdateFormat.format(birthdate);
    }



    public Gender getGender() {
        return gender;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Person: ");
        sb.append("id=").append(getId().orElse(null));
        sb.append(", firstName=").append(firstName);
        sb.append(", lastName=").append(lastName);
        sb.append(", birthdate=").append(birthdate);
        sb.append(", gender=").append(gender);
        return sb.toString();
    }
}
