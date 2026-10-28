package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.db.repository.StoreRepository;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// Stores belong to a list, and can only be used on that list's items. Like the
// lists themselves, every user can see and change them.
public class StoreService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;
    private ListChanges changes;
    private StoreRepository storeRepository = new StoreRepository();
    private ItemRepository itemRepository = new ItemRepository();

    public StoreService(Db db) {
        this(db, ListChanges.NONE);
    }

    // Giving an item a store, or taking one away, is passed on to changes.
    public StoreService(Db db, ListChanges changes) {
        this.db = db;
        this.changes = changes;
    }

    public Store createStore(Person person, int itemListId, String name) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            requireList(conn, itemListId);

            int storeId = storeRepository.createStore(conn, person.id(), itemListId, name);
            return storeRepository.getStore(conn, storeId).orElseThrow(
                    () -> new RuntimeException("Failed to create store")
            );
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Store getStore(int storeId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            return existingStore(conn, storeId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The stores of a list, sorted by name.
    public List<Store> getStores(int itemListId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            requireList(conn, itemListId);
            return storeRepository.getStoresForList(conn, itemListId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void patchStore(int storeId, String newName) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            existingStore(conn, storeId);
            storeRepository.updateStore(conn, storeId, newName);
            // The items show the new name.
            changes.listsChanged();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteStore(int storeId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            existingStore(conn, storeId);
            storeRepository.deleteStore(conn, storeId);
            changes.listsChanged();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The store must be on the same list as the item.
    public void mapStoreAndItem(Person person, int storeId, int itemId)
            throws ResourceNotFoundException, NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            Store store = existingStore(conn, storeId);
            int itemListId = itemListIdOfItem(conn, itemId);
            if (store.getItemListId() != itemListId) {
                throw new NoAccessToResourceException("Store is not on the item's list");
            }

            if (storeRepository.mapStoreToItem(conn, storeId, itemId)) {
                itemStoresChanged(conn, person, itemListId, itemId);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void unmapStoreFromItem(Person person, int storeId, int itemId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            int itemListId = itemListIdOfItem(conn, itemId);
            if (storeRepository.unmapStoreFromItem(conn, storeId, itemId)) {
                itemStoresChanged(conn, person, itemListId, itemId);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void itemStoresChanged(Connection conn, Person person, int itemListId, int itemId) {
        itemRepository.getItem(conn, itemId).ifPresent(
                item -> changes.itemChanged(ListChanges.ItemEvent.ITEM_CHANGED, itemListId, item, person)
        );
    }

    // A store on a deleted list is reported as not found.
    private Store existingStore(Connection conn, int storeId) throws ResourceNotFoundException {
        Store store = storeRepository.getStore(conn, storeId).orElseThrow(
                () -> new ResourceNotFoundException("Store not found")
        );
        requireList(conn, store.getItemListId());
        return store;
    }

    private int itemListIdOfItem(Connection conn, int itemId) throws ResourceNotFoundException {
        if (itemRepository.getItem(conn, itemId).isEmpty()) {
            throw new ResourceNotFoundException("Item not found");
        }
        return itemRepository.getItemListIdOfItem(conn, itemId).orElseThrow(
                () -> new ResourceNotFoundException("Item not found")
        );
    }

    private void requireList(Connection conn, int itemListId) throws ResourceNotFoundException {
        if (!itemRepository.itemListExists(conn, itemListId)) {
            throw new ResourceNotFoundException("Item list not found");
        }
    }
}
