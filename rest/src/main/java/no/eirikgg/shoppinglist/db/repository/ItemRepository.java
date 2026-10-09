package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.db.DbUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ItemRepository {
    private DbUtils dbUtils = new DbUtils();

    public int createItemList(Connection conn, UUID sessionId, int personId, String name) throws SQLException {
        return dbUtils.insert(conn, """
            insert into item_list (
                session_id,
                person_id,
                name,
                created
            ) values (?,?,?,?)
            """,
            sessionId,
            personId,
            name,
            LocalDateTime.now()
        ).orElseThrow();
    }

    public Optional<ItemList> getItemList(Connection conn, int itemListId) {
        return dbUtils.selectSingle(conn, """
            select item_list_id, name
            from item_list
            where item_list_id = ?
            and deleted is null
            """,
            itemListId,
            rs -> new ItemList(
                rs.getInt("item_list_id").orElseThrow(),
                rs.getString("name").orElseThrow()
            )
        );
    }

    public List<Integer> getItemListForPerson(Connection conn, int personId) {
        return dbUtils.select(conn, """
            select item_list_id
            from item_list
            where person_id = ?
            and deleted is null
            """,
            personId,
            rs -> rs.getInt("item_list_id").orElseThrow()
        );
    }

    public int deleteItemList(Connection conn, Integer personId, int itemListId) throws SQLException {
        return dbUtils.update(conn, """
            update item_list
            set deleted = ?
            where person_id = ?
            and item_list_id = ?
            """,
            LocalDateTime.now(),
            personId,
            itemListId
        ).orElse(0);
    }
}