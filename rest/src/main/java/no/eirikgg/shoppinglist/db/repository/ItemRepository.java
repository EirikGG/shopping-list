package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.Store;
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
        Optional<ItemList> itemList = dbUtils.selectSingle(conn, """
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
        itemList.ifPresent(list -> list.getItems().addAll(getItemsForItemList(conn, itemListId)));
        return itemList;
    }

    public List<Item> getItemsForItemList(Connection conn, int itemListId) {
        return dbUtils.select(conn, """
            select item_id, name, completed
            from item
            where item_list_id = ?
            and deleted is null
            order by item_id
            """,
            itemListId,
            rs -> {
                Integer itemId = rs.getInt("item_id").orElseThrow();
                return new Item(
                        itemId,
                        rs.getString("name").orElseThrow(),
                        rs.getLocalDateTime("completed").orElse(null),
                        getStoresForItemList(conn, itemId)
                );
            }
        );
    }

    private List<Store> getStoresForItemList(Connection conn, Integer itemId) {
        return dbUtils.select(conn, """
                select 
                    mapping.store_id
                    , s.person_id
                    , s.name
                    , s.created
                from item_store_mapping as mapping
                join store as s 
                    on s.store_id = mapping.store_id 
                where mapping.item_id = ?
                and s.deleted is null
                order by lower(s.name), s.store_id
                """,
                itemId,
                rs -> new Store(
                        rs.getInt("store_id").orElseThrow(),
                        rs.getInt("person_id").orElseThrow(),
                        rs.getString("name").orElseThrow(),
                        rs.getLocalDateTime("created").orElseThrow()
                )
        );
    }

    // Every list the person has access to, their own and those shared with them.
    public List<Integer> getItemListForPerson(Connection conn, int personId) {
        return dbUtils.select(conn, """
            select il.item_list_id
            from item_list_person as ilp
            join item_list as il
                on il.item_list_id = ilp.item_list_id
            where ilp.person_id = ?
            and il.deleted is null
            order by il.item_list_id
            """,
            personId,
            rs -> rs.getInt("item_list_id").orElseThrow()
        );
    }

    // Access is checked by the caller, through ItemListPersonRepository.
    public int deleteItemList(Connection conn, int itemListId) throws SQLException {
        return dbUtils.update(conn, """
            update item_list
            set deleted = ?
            where item_list_id = ?
            and deleted is null
            """,
            LocalDateTime.now(),
            itemListId
        ).orElse(0);
    }

    // Access is checked by the caller, through ItemListPersonRepository.
    public int updateItemListName(Connection conn, int itemListId, String newName) throws SQLException {
        return dbUtils.update(conn, """
                update item_list
                set name                = ?
                where item_list_id      = ?
                    and deleted is null
                """,
                newName,
                itemListId
        ).orElse(0);
    }

    // Empty unless the person has access to the item's list.
    public Optional<Item> getItem(Connection conn, Integer personId, int itemId) {
        return dbUtils.selectSingle(conn, """
                select i.item_id, i.name, i.completed
                from item as i
                join item_list_person as ilp
                    on ilp.item_list_id = i.item_list_id
                    and ilp.person_id = ?
                join item_list as il
                    on il.item_list_id = i.item_list_id
                    and il.deleted is null
                where i.item_id = ?
                and i.deleted is null
                """,
                new Object[] {personId, itemId},
                rs -> new Item(
                        rs.getInt("item_id").orElseThrow(),
                        rs.getString("name").orElseThrow(),
                        rs.getLocalDateTime("completed").orElse(null),
                        getStoresForItemList(conn, rs.getInt("item_id").orElseThrow())
                )
        );
    }

    public int createItem(Connection conn, UUID sessionId, Integer personId, int itemListId, String name)
            throws SQLException {
        return dbUtils.insert(conn, """
                insert into item (
                       item_list_id,
                       session_id,
                       person_id,
                       name,
                       created
                )
                values (?, ?, ?, ?, ?)
                """,
                itemListId,
                sessionId,
                personId,
                name,
                LocalDateTime.now()
        ).orElseThrow();
    }

    // Only deletes if the person has access to the item's list.
    public void deleteItem(Connection conn, Integer personId, int itemId) throws SQLException {
        dbUtils.update(conn, """
                update item
                set deleted = ?
                where item_id = ?
                and item_list_id in (
                    select item_list_id from item_list_person where person_id = ?
                )
                """,
                LocalDateTime.now(),
                itemId,
                personId
        );
    }

    public void patchItem(
            Connection conn, Integer personId, int itemId, String newName, LocalDateTime completed
    ) throws SQLException {
        dbUtils.update(conn, """
                update item
                set name = ?, completed = ?
                where item_id = ?
                and item_list_id in (
                    select item_list_id from item_list_person where person_id = ?
                )
                """,
                newName,
                completed,
                itemId,
                personId
        );
    }

}