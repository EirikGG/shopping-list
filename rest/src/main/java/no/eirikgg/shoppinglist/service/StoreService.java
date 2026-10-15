package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.Session;
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
import java.util.Optional;
import java.util.UUID;

public class StoreService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;
    private StoreRepository storeRepository = new StoreRepository();
    private ItemRepository itemRepository = new ItemRepository();

    public StoreService(Db db) {
        this.db = db;
    }

    public Store createStore(Session session, String name) {
        try (Connection conn = db.getConnection()) {
            UUID sessionId = session.getSessionId();
            int personId = session.getUser().getPerson().getId().orElseThrow();
            int storeId = storeRepository.createStore(conn, sessionId, personId, name);
            return storeRepository.getStore(conn, storeId).orElseThrow(
                    () -> new RuntimeException("Failed to create store")
            );
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Store getStore(Session session, int storeId) throws NoAccessToResourceException, ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            Optional<Store> store = storeRepository.getStore(conn, storeId);
            if (store.isEmpty()) {
                throw new ResourceNotFoundException("Store not found");
            }

            checkStoreAccess(session, store.orElseThrow());
            return store.orElseThrow();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void patchStore(Session session, int storeId, String newName)
            throws ResourceNotFoundException, NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            Optional<Store> store = storeRepository.getStore(conn, storeId);
            if (store.isEmpty()) {
                throw new ResourceNotFoundException("Store not found");
            }

            checkStoreAccess(session, store.orElseThrow());
            storeRepository.updateStore(conn, storeId, newName);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteStore(Session session, int storeId)
            throws ResourceNotFoundException, NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            Optional<Store> store = storeRepository.getStore(conn, storeId);
            if (store.isEmpty()) {
                throw new ResourceNotFoundException("Store not found");
            }

            checkStoreAccess(session, store.orElseThrow());
            storeRepository.deleteStore(conn, storeId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void mapStoreAndItem(Session session, int storeId, int itemId)
            throws ResourceNotFoundException, NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            Optional<Store> store = storeRepository.getStore(conn, storeId);
            Integer personId = session.getUser().getPerson().getId().orElseThrow();
            UUID sessionId = session.getSessionId();
            Optional<Item> item = itemRepository.getItem(conn, personId, itemId);

            if (store.isEmpty()) {
                throw new ResourceNotFoundException("Store not found");
            }
            if (item.isEmpty()) {
                throw new ResourceNotFoundException("Item not found");
            }

            checkStoreAccess(session, store.orElseThrow());
            storeRepository.mapStoreToItem(conn, sessionId, storeId, itemId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkStoreAccess(Session session, Store store) throws NoAccessToResourceException {
        int personId = session.getUser().getPerson().getId().orElseThrow();

        if (null == store) {
            return;
        }

        if (store.getPersonId() != personId) {
            throw new NoAccessToResourceException("No access to resource");
        }
    }
}
