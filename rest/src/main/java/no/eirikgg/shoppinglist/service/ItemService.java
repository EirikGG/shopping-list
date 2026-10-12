package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.AuthenticationRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.errors.UserNotLoggedInException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ItemService {
    private Db db;

    private ItemRepository itemRepository = new ItemRepository();
    private AuthenticationRepository authenticationRepository = new AuthenticationRepository();

    public ItemService(Db db) {
        this.db = db;
    }

    public ItemList createItemList(Session session, String name) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );

            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );
            int itemListId = itemRepository.createItemList(conn, sessionId, personId, name);
            ItemList itemList = itemRepository.getItemList(conn, itemListId).orElseThrow(
                    () -> new RuntimeException("Failed to create item list")
            );

            return itemList;
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ItemList> getItemLists(Session session) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );
            List<Integer> itemListIds = itemRepository.getItemListForPerson(conn, personId);

            return itemListIds.stream()
                    .map(id -> itemRepository.getItemList(conn, id))
                    .map(Optional::orElseThrow)
                    .toList();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteItemList(Session session, int itemListId) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            try {
                conn.setAutoCommit(false);
                int updateCount = itemRepository.deleteItemList(conn, personId, itemListId);
                if (1 != updateCount) {
                    String message = "Did not delete a single item list with id " + itemListId;
                    throw new RuntimeException(message);
                }
                conn.commit();
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

    public void updateItemList(Session session, int itemListId, String newName) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            try {
                conn.setAutoCommit(false);
                int updateCount = itemRepository.updateItemListName(conn, personId, itemListId, newName);
                if (1 != updateCount) {
                    String message = "Did not update a single list - rolling back";
                    throw new RuntimeException(message);
                }
                conn.commit();
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

    public Optional<Item> getItem(Session session, int itemId) {
        try (Connection conn = db.getConnection()) {
            UUID sessionId = session.getSessionId();
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            return itemRepository.getItem(conn, personId, itemId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Item createItem(Session session, int itemListId, String name) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            int listOwnerPersonId = itemRepository.getListOwnerPersonId(conn, itemListId).orElseThrow(
                    () -> new RuntimeException("Cant find list owner for list with id " + itemListId)
            );

            if (listOwnerPersonId != personId) {
                throw new RuntimeException("Cant update list without being the owner");
            }

            int itemId = itemRepository.createItem(conn, sessionId, personId, itemListId, name);
            return itemRepository.getItem(conn, personId, itemId).orElseThrow();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteItem(Session session, int itemId) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            itemRepository.deleteItem(conn, personId, itemId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Item patchItem(Session session, int itemId, String newName, LocalDateTime completed) {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            itemRepository.patchItem(conn, personId, itemId, newName, completed);
            return itemRepository.getItem(conn, personId, itemId).orElseThrow();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
