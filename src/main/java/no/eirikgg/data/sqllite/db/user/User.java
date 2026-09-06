package no.eirikgg.data.sqllite.db.user;

public class User {

    private Person person;
    private Authorization authorization;
    private Email email;
    private PhoneNumber phonenumber;


    public User(
            Person person,
            Authorization authorization,
            Email email,
            PhoneNumber phonenumber
    ) {
        this.person = person;
        this.authorization = authorization;
        this.email = email;
        this.phonenumber = phonenumber;
    }


    public Person getPerson() {
        return person;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public Email getEmail() {
        return email;
    }

    public PhoneNumber getPhoneNumber() {
        return phonenumber;
    }
}
