package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.StoreRepository;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
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

    public Optional<Store> getStore(Session session, int storeId) throws NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            Optional<Store> store = storeRepository.getStore(conn, storeId);
            int personId = session.getUser().getPerson().getId().orElseThrow();

            if (store.isEmpty()) {
                return Optional.empty();
            }

            if (store.orElseThrow().getPersonId() != personId) {
                throw new NoAccessToResourceException("No access to resource");
            }

            return store;
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
