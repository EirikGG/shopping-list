package no.eirikgg.shoppinglist.datamodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.eirikgg.shoppinglist.db.DbIdentity;

public class Person extends DbIdentity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String firstName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String lastName;

    public Person(Integer id, String firstName, String lastName) {
        super(id);
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Person(String firstName, String lastName) {
        this(null, firstName, lastName);
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Person: ");
        sb.append("id=").append(getId().orElse(null));
        sb.append(", firstName=").append(firstName);
        sb.append(", lastName=").append(lastName);
        return sb.toString();
    }
}
