package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.db.repository.StoreRepository;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Every Home Assistant user sees and changes every list, as one household. The
// person is only recorded as who created a list or an item.
public class ItemService {
    private Db db;

    private ItemRepository itemRepository = new ItemRepository();
    private StoreRepository storeRepository = new StoreRepository();

    public ItemService(Db db) {
        this.db = db;
    }

    public ItemList createItemList(Person person, String name) {
        try (Connection conn = db.getConnection()) {
            int itemListId = itemRepository.createItemList(conn, person.id(), name);
            return itemRepository.getItemList(conn, itemListId).orElseThrow(
                    () -> new RuntimeException("Cant find item list with id " + itemListId)
            );
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ItemList> getItemLists() {
        try (Connection conn = db.getConnection()) {
            return itemRepository.getItemListIds(conn).stream()
                    .map(id -> itemRepository.getItemList(conn, id).orElseThrow(
                            () -> new RuntimeException("Cant find item list with id " + id)
                    ))
                    .toList();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteItemList(int itemListId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            if (0 == itemRepository.deleteItemList(conn, itemListId)) {
                throw new ResourceNotFoundException("Item list not found");
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateItemList(int itemListId, String newName) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            if (0 == itemRepository.updateItemListName(conn, itemListId, newName)) {
                throw new ResourceNotFoundException("Item list not found");
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Item> getItem(int itemId) {
        try (Connection conn = db.getConnection()) {
            return itemRepository.getItem(conn, itemId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // storeIds may be null or empty. The item is connected to the stores in the
    // same transaction, so an item is never created without its stores.
    public Item createItem(Person person, int itemListId, String name, List<Integer> storeIds)
            throws ResourceNotFoundException, NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            if (!itemRepository.itemListExists(conn, itemListId)) {
                throw new ResourceNotFoundException("Item list not found");
            }

            List<Integer> stores = null == storeIds ? List.of() : storeIds;
            for (int storeId : stores) {
                Store store = storeRepository.getStore(conn, storeId).orElseThrow(
                        () -> new ResourceNotFoundException("Store not found")
                );
                // Only the list's own stores.
                if (store.getItemListId() != itemListId) {
                    throw new NoAccessToResourceException("Store is not on this list");
                }
            }

            try {
                conn.setAutoCommit(false);
                int itemId = itemRepository.createItem(conn, person.id(), itemListId, name);
                for (int storeId : stores) {
                    storeRepository.mapStoreToItem(conn, storeId, itemId);
                }
                conn.commit();
                return itemRepository.getItem(conn, itemId).orElseThrow();
            }
            catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteItem(int itemId) {
        try (Connection conn = db.getConnection()) {
            itemRepository.deleteItem(conn, itemId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Item patchItem(int itemId, String newName, LocalDateTime completed) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            if (itemRepository.getItem(conn, itemId).isEmpty()) {
                throw new ResourceNotFoundException("Item not found");
            }
            itemRepository.patchItem(conn, itemId, newName, completed);
            return itemRepository.getItem(conn, itemId).orElseThrow();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
