package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.DbUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class StoreRepository {

    private DbUtils dbUtils = new DbUtils();

    public int createStore(Connection conn, UUID sessionId, int personId, String name) throws SQLException {
        return dbUtils.insert(conn, """
                insert into store (
                    session_id,
                    person_id,
                    name,
                    created
                ) values (?, ?, ?, ?)
                """,
                sessionId,
                personId,
                name,
                LocalDateTime.now()
        ).orElseThrow();
    }

    public Optional<Store> getStore(Connection conn, int storeId) {
        return dbUtils.selectSingle(conn, """
                select   
                    store_id,
                    person_id,
                    name,
                    created
                from store
                where store_id = ?
                and deleted is null
                """,
                storeId,
                rs -> new Store(
                        rs.getInt("store_id").orElseThrow(),
                        rs.getInt("person_id").orElseThrow(),
                        rs.getString("name").orElseThrow(),
                        rs.getLocalDateTime("created").orElseThrow()
                )
        );
    }

    public void updateStore(Connection conn, int storeId, String newName) throws SQLException {
        dbUtils.update(conn, """
            update store
            set name = ?
            where store_id = ?
            """,
            newName,
            storeId
        );
    }

    public void deleteStore(Connection conn, int storeId) throws SQLException {
        dbUtils.update(conn, """
            update store
            set deleted = ?
            where store_id = ?
            """,
            LocalDateTime.now(),
            storeId
        );
    }

    public void mapStoreToItem(Connection conn, UUID sessionId, int storeId, int itemId) throws SQLException {
        dbUtils.insert(conn, """
            insert into item_store_mapping (
                 session_id,
                 item_id,
                 store_id
            ) values (?, ?, ?)
            """,
            sessionId,
            itemId,
            storeId
        );
    }
}
