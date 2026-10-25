package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.ItemListPersonRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.db.repository.StoreRepository;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

// Stores belong to a list. Everyone with access to the list can see, add,
// rename and delete its stores, and use them on the list's items. To anyone
// else a store does not exist.
public class StoreService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;
    private StoreRepository storeRepository = new StoreRepository();
    private ItemRepository itemRepository = new ItemRepository();
    private ItemListPersonRepository itemListPersonRepository = new ItemListPersonRepository();

    public StoreService(Db db) {
        this.db = db;
    }

    public Store createStore(Session session, int itemListId, String name) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            UUID sessionId = session.getSessionId();
            int personId = personId(session);
            requireListAccess(conn, itemListId, personId);

            int storeId = storeRepository.createStore(conn, sessionId, personId, itemListId, name);
            return storeRepository.getStore(conn, storeId).orElseThrow(
                    () -> new RuntimeException("Failed to create store")
            );
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Store getStore(Session session, int storeId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            return accessibleStore(conn, session, storeId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The stores of a list, sorted by name.
    public List<Store> getStores(Session session, int itemListId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            requireListAccess(conn, itemListId, personId(session));
            return storeRepository.getStoresForList(conn, itemListId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void patchStore(Session session, int storeId, String newName) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            accessibleStore(conn, session, storeId);
            storeRepository.updateStore(conn, storeId, newName);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteStore(Session session, int storeId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            accessibleStore(conn, session, storeId);
            storeRepository.deleteStore(conn, storeId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The store must be on the same list as the item.
    public void mapStoreAndItem(Session session, int storeId, int itemId)
            throws ResourceNotFoundException, NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            Store store = accessibleStore(conn, session, storeId);
            int itemListId = accessibleItemListId(conn, session, itemId);
            if (store.getItemListId() != itemListId) {
                throw new NoAccessToResourceException("Store is not on the item's list");
            }

            storeRepository.mapStoreToItem(conn, session.getSessionId(), storeId, itemId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void unmapStoreFromItem(Session session, int storeId, int itemId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            accessibleItemListId(conn, session, itemId);
            storeRepository.unmapStoreFromItem(conn, storeId, itemId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Store accessibleStore(Connection conn, Session session, int storeId) throws ResourceNotFoundException {
        Store store = storeRepository.getStore(conn, storeId).orElseThrow(
                () -> new ResourceNotFoundException("Store not found")
        );
        requireListAccess(conn, store.getItemListId(), personId(session));
        return store;
    }

    // The list of an item the person has access to.
    private int accessibleItemListId(Connection conn, Session session, int itemId) throws ResourceNotFoundException {
        if (itemRepository.getItem(conn, personId(session), itemId).isEmpty()) {
            throw new ResourceNotFoundException("Item not found");
        }
        return itemRepository.getItemListIdOfItem(conn, itemId).orElseThrow(
                () -> new ResourceNotFoundException("Item not found")
        );
    }

    // Without access, a list and its stores are reported as not found, so
    // their existence is not given away.
    private void requireListAccess(Connection conn, int itemListId, int personId) throws ResourceNotFoundException {
        if (itemListPersonRepository.getRole(conn, itemListId, personId).isEmpty()) {
            throw new ResourceNotFoundException("Item list not found");
        }
    }

    private static int personId(Session session) {
        return session.getUser().getPerson().getId().orElseThrow();
    }
}
