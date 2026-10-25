package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.ItemListMember;
import no.eirikgg.shoppinglist.db.DbUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Connects lists to the persons who have access to them.
public class ItemListPersonRepository {
    private DbUtils dbUtils = new DbUtils();

    // Adding a person who already has access does nothing.
    public void addPerson(
            Connection conn, UUID sessionId, int itemListId, int personId, ItemListMember.Role role
    ) throws SQLException {
        dbUtils.update(conn, """
            insert into item_list_person (
                item_list_id,
                person_id,
                role,
                session_id,
                created
            ) values (?, ?, ?, ?, ?)
            on conflict (item_list_id, person_id) do nothing
            """,
            itemListId,
            personId,
            role.name(),
            sessionId,
            LocalDateTime.now()
        );
    }

    public int removePerson(Connection conn, int itemListId, int personId) throws SQLException {
        return dbUtils.update(conn, """
            delete from item_list_person
            where item_list_id = ?
            and person_id = ?
            """,
            itemListId,
            personId
        ).orElse(0);
    }

    // Empty if the person has no access, or the list is deleted.
    public Optional<ItemListMember.Role> getRole(Connection conn, int itemListId, int personId) {
        return dbUtils.selectSingle(conn, """
            select ilp.role
            from item_list_person as ilp
            join item_list as il
                on il.item_list_id = ilp.item_list_id
            where ilp.item_list_id = ?
            and ilp.person_id = ?
            and il.deleted is null
            """,
            new Object[] {itemListId, personId},
            rs -> ItemListMember.Role.valueOf(rs.getString("role").orElseThrow())
        );
    }

    // The owner first, then the members by name.
    public List<ItemListMember> getMembers(Connection conn, int itemListId) {
        return dbUtils.select(conn, """
            select
                p.person_id,
                p.firstname,
                p.lastname,
                (
                    select min(login.email)
                    from person_login as login
                    where login.person_id = p.person_id
                ) as email,
                ilp.role
            from item_list_person as ilp
            join person as p
                on p.person_id = ilp.person_id
            where ilp.item_list_id = ?
            order by
                case when ilp.role = 'OWNER' then 0 else 1 end,
                lower(p.firstname),
                lower(p.lastname),
                p.person_id
            """,
            itemListId,
            rs -> new ItemListMember(
                    rs.getInt("person_id").orElseThrow(),
                    rs.getString("firstname").orElseThrow(),
                    rs.getString("lastname").orElseThrow(),
                    rs.getString("email").orElse(null),
                    ItemListMember.Role.valueOf(rs.getString("role").orElseThrow())
            )
        );
    }

    // The person who logs in with the email, ignoring case.
    public Optional<Integer> findPersonIdByEmail(Connection conn, String email) {
        return dbUtils.selectSingle(conn, """
            select min(person_id) as person_id
            from person_login
            where lower(email) = lower(?)
            having count(*) > 0
            """,
            email,
            rs -> rs.getInt("person_id").orElseThrow()
        );
    }
}
