package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.RSWrapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class ItemRepository {
    private DbUtils dbUtils = new DbUtils();
    private ItemLinkRepository itemLinkRepository = new ItemLinkRepository();
    private AttachmentRepository attachmentRepository = new AttachmentRepository();

    public int createItemList(Connection conn, int personId, String name) throws SQLException {
        return dbUtils.insert(conn, """
            insert into item_list (
                person_id,
                name,
                created
            ) values (?,?,?)
            """,
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

    // Open items first, newest added first; then completed items, last
    // completed first. Timestamps are fixed-width text, so they sort as text.
    public List<Item> getItemsForItemList(Connection conn, int itemListId) {
        return dbUtils.select(conn, """
            select item_id, name, amount, created, completed
            from item
            where item_list_id = ?
            and deleted is null
            order by
                completed is not null,
                coalesce(completed, created) desc,
                item_id desc
            """,
            itemListId,
            rs -> toItem(conn, rs)
        );
    }

    // Reads item_id, name, amount, created and completed, and adds the item's stores,
    // links and files.
    private Item toItem(Connection conn, RSWrapper rs) {
        int itemId = rs.getInt("item_id").orElseThrow();
        Item item = new Item(
                itemId,
                rs.getString("name").orElseThrow(),
                rs.getInt("amount").orElseThrow(),
                rs.getLocalDateTime("created").orElseThrow(),
                rs.getLocalDateTime("completed").orElse(null),
                getStoresForItemList(conn, itemId)
        );
        item.getLinks().addAll(itemLinkRepository.getLinksForItem(conn, itemId));
        item.getAttachments().addAll(attachmentRepository.getAttachmentsForItem(conn, itemId));
        return item;
    }

    private List<Store> getStoresForItemList(Connection conn, Integer itemId) {
        return dbUtils.select(conn, """
                select 
                    mapping.store_id
                    , s.person_id
                    , s.item_list_id
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
                StoreRepository::toStore
        );
    }

    // The list an item is on, if the item exists.
    public Optional<Integer> getItemListIdOfItem(Connection conn, int itemId) {
        return dbUtils.selectSingle(conn,
                "select item_list_id from item where item_id = ? and deleted is null",
                itemId,
                rs -> rs.getInt("item_list_id").orElseThrow()
        );
    }

    // Every list, as every user sees all of them.
    public List<Integer> getItemListIds(Connection conn) {
        return dbUtils.select(conn, """
            select item_list_id
            from item_list
            where deleted is null
            order by item_list_id
            """,
            new Object[] {},
            rs -> rs.getInt("item_list_id").orElseThrow()
        );
    }

    public List<Integer> getDeletedItemListIds(Connection conn) {
        return dbUtils.select(conn, """
            select item_list_id
            from item_list
            where deleted is not null
            order by item_list_id
            """,
            new Object[] {},
            rs -> rs.getInt("item_list_id").orElseThrow()
        );
    }

    public boolean itemListExists(Connection conn, int itemListId) {
        return dbUtils.selectSingle(conn, """
            select item_list_id
            from item_list
            where item_list_id = ?
            and deleted is null
            """,
            itemListId,
            rs -> rs.getInt("item_list_id").orElseThrow()
        ).isPresent();
    }

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

    // Empty if the item or its list is deleted.
    public Optional<Item> getItem(Connection conn, int itemId) {
        return dbUtils.selectSingle(conn, """
                select i.item_id, i.name, i.amount, i.created, i.completed
                from item as i
                join item_list as il
                    on il.item_list_id = i.item_list_id
                    and il.deleted is null
                where i.item_id = ?
                and i.deleted is null
                """,
                itemId,
                rs -> toItem(conn, rs)
        );
    }

    public int createItem(Connection conn, int personId, int itemListId, String name, int amount)
            throws SQLException {
        return dbUtils.insert(conn, """
                insert into item (
                       item_list_id,
                       person_id,
                       name,
                       amount,
                       created
                )
                values (?, ?, ?, ?, ?)
                """,
                itemListId,
                personId,
                name,
                amount,
                LocalDateTime.now()
        ).orElseThrow();
    }

    public void deleteItem(Connection conn, int itemId) throws SQLException {
        dbUtils.update(conn, """
                update item
                set deleted = ?
                where item_id = ?
                """,
                LocalDateTime.now(),
                itemId
        );
    }

    public void patchItem(
            Connection conn, int itemId, String newName, int amount, LocalDateTime completed
    ) throws SQLException {
        dbUtils.update(conn, """
                update item
                set name = ?, amount = ?, completed = ?
                where item_id = ?
                """,
                newName,
                amount,
                completed,
                itemId
        );
    }

}