package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.db.DbUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

public class ItemRepository {
    private DbUtils dbUtils = new DbUtils();

    public void createItemList(Connection conn, UUID sessionId, String name) throws SQLException {
        dbUtils.insert(conn, """
            insert into item_list (
                session_id,
                name,
                created
            ) values (?,?,?)
            """,
            sessionId,
            name,
            LocalDateTime.now()
        );
    }
}