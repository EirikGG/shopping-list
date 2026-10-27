package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.DbUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

public class PersonRepository {
    private DbUtils dbUtils = new DbUtils();

    public Optional<Person> findByHaUserId(Connection conn, String haUserId) {
        return dbUtils.selectSingle(conn, """
            select person_id, ha_user_id, name
            from person
            where ha_user_id = ?
            """,
            haUserId,
            rs -> new Person(
                    rs.getInt("person_id").orElseThrow(),
                    rs.getString("ha_user_id").orElseThrow(),
                    rs.getString("name").orElse(null)
            )
        );
    }

    // A second insert for the same user does nothing, so two first requests
    // arriving together do not fail.
    public void create(Connection conn, String haUserId, String name) throws SQLException {
        dbUtils.update(conn, """
            insert into person (ha_user_id, name, created)
            values (?, ?, ?)
            on conflict (ha_user_id) do nothing
            """,
            haUserId,
            name,
            LocalDateTime.now()
        );
    }

    public void updateName(Connection conn, int personId, String name) throws SQLException {
        dbUtils.update(conn, """
            update person
            set name = ?
            where person_id = ?
            """,
            name,
            personId
        );
    }
}
