package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.RSWrapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Stores belong to a list. Access is checked by the caller, through the list.
public class StoreRepository {

    private DbUtils dbUtils = new DbUtils();

    public int createStore(
            Connection conn, UUID sessionId, int personId, int itemListId, String name
    ) throws SQLException {
        return dbUtils.insert(conn, """
                insert into store (
                    session_id,
                    person_id,
                    item_list_id,
                    name,
                    created
                ) values (?, ?, ?, ?, ?)
                """,
                sessionId,
                personId,
                itemListId,
                name,
                LocalDateTime.now()
        ).orElseThrow();
    }

    public Optional<Store> getStore(Connection conn, int storeId) {
        return dbUtils.selectSingle(conn, """
                select
                    store_id,
                    person_id,
                    item_list_id,
                    name,
                    created
                from store
                where store_id = ?
                and deleted is null
                """,
                storeId,
                StoreRepository::toStore
        );
    }

    // The stores of a list, sorted by name.
    public List<Store> getStoresForList(Connection conn, int itemListId) {
        return dbUtils.select(conn, """
                select
                    store_id,
                    person_id,
                    item_list_id,
                    name,
                    created
                from store
                where item_list_id = ?
                and deleted is null
                order by lower(name), store_id
                """,
                itemListId,
                StoreRepository::toStore
        );
    }

    // Reads store_id, person_id, item_list_id, name and created.
    static Store toStore(RSWrapper rs) {
        return new Store(
                rs.getInt("store_id").orElseThrow(),
                rs.getInt("person_id").orElseThrow(),
                rs.getInt("item_list_id").orElseThrow(),
                rs.getString("name").orElseThrow(),
                rs.getLocalDateTime("created").orElseThrow()
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

    // Mapping an item to a store it already has does nothing.
    public void mapStoreToItem(Connection conn, UUID sessionId, int storeId, int itemId) throws SQLException {
        dbUtils.update(conn, """
            insert into item_store_mapping (
                 session_id,
                 item_id,
                 store_id
            ) values (?, ?, ?)
            on conflict (item_id, store_id) do nothing
            """,
            sessionId,
            itemId,
            storeId
        );
    }

    public void unmapStoreFromItem(Connection conn, int storeId, int itemId) throws SQLException {
        dbUtils.update(conn, """
            delete from item_store_mapping
            where store_id = ?
            and item_id = ?
            """,
            storeId,
            itemId
        );
    }
}
