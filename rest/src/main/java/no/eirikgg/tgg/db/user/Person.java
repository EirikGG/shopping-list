package no.eirikgg.tgg.db.user;

import no.eirikgg.tgg.db.DbIdentity;

import java.time.LocalDate;

public class Person extends DbIdentity {

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    private String firstName;
    private String lastName;
    private LocalDate birthdate;
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
