package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.PersonRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class PersonService {
    private Db db;
    private PersonRepository personRepository = new PersonRepository();

    public PersonService(Db db) {
        this.db = db;
    }

    // The person for a Home Assistant user, created on their first request.
    // The name follows the user's display name in HA.
    public Person getOrCreate(String haUserId, String name) {
        try (Connection conn = db.getConnection()) {
            Person person = personRepository.findByHaUserId(conn, haUserId).orElse(null);
            if (null == person) {
                personRepository.create(conn, haUserId, name);
                return personRepository.findByHaUserId(conn, haUserId).orElseThrow();
            }

            if (!Objects.equals(person.name(), name)) {
                personRepository.updateName(conn, person.id(), name);
                return new Person(person.id(), haUserId, name);
            }
            return person;
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
