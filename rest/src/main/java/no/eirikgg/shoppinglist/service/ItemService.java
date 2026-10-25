package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.ItemListMember;
import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.AuthenticationRepository;
import no.eirikgg.shoppinglist.db.repository.ItemListPersonRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.db.repository.StoreRepository;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
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
    private ItemListPersonRepository itemListPersonRepository = new ItemListPersonRepository();
    private StoreRepository storeRepository = new StoreRepository();
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
            try {
                conn.setAutoCommit(false);
                int itemListId = itemRepository.createItemList(conn, sessionId, personId, name);
                itemListPersonRepository.addPerson(
                        conn, sessionId, itemListId, personId, ItemListMember.Role.OWNER
                );
                conn.commit();
                return getItemListWithMembers(conn, itemListId, personId);
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
                    .map(id -> getItemListWithMembers(conn, id, personId))
                    .toList();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Only the owner can delete a list. It is then gone for the members too.
    public void deleteItemList(Session session, int itemListId)
            throws ResourceNotFoundException, NoAccessToResourceException {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            requireRole(conn, itemListId, personId, ItemListMember.Role.OWNER);

            try {
                conn.setAutoCommit(false);
                int updateCount = itemRepository.deleteItemList(conn, itemListId);
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

    // Everyone with access to the list can rename it.
    public void updateItemList(Session session, int itemListId, String newName)
            throws ResourceNotFoundException, NoAccessToResourceException {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            requireRole(conn, itemListId, personId, ItemListMember.Role.MEMBER);

            try {
                conn.setAutoCommit(false);
                int updateCount = itemRepository.updateItemListName(conn, itemListId, newName);
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

    // storeIds may be null or empty. The item is connected to the stores in the
    // same transaction, so an item is never created without its stores.
    public Item createItem(Session session, int itemListId, String name, List<Integer> storeIds)
            throws ResourceNotFoundException, NoAccessToResourceException {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            requireRole(conn, itemListId, personId, ItemListMember.Role.MEMBER);

            List<Integer> stores = null == storeIds ? List.of() : storeIds;
            for (int storeId : stores) {
                Store store = storeRepository.getStore(conn, storeId).orElseThrow(
                        () -> new ResourceNotFoundException("Store not found")
                );
                // Only the list's own stores, which everyone on the list shares.
                if (store.getItemListId() != itemListId) {
                    throw new NoAccessToResourceException("Store is not on this list");
                }
            }

            try {
                conn.setAutoCommit(false);
                int itemId = itemRepository.createItem(conn, sessionId, personId, itemListId, name);
                for (int storeId : stores) {
                    storeRepository.mapStoreToItem(conn, sessionId, storeId, itemId);
                }
                conn.commit();
                return itemRepository.getItem(conn, personId, itemId).orElseThrow();
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

    // Gives the person who logs in with the email access to the list. Only the
    // owner can share. Sharing with someone who already has access does nothing.
    public ItemListMember shareItemList(Session session, int itemListId, String email)
            throws ResourceNotFoundException, NoAccessToResourceException {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            requireRole(conn, itemListId, personId, ItemListMember.Role.OWNER);

            int memberPersonId = itemListPersonRepository.findPersonIdByEmail(conn, email.trim()).orElseThrow(
                    () -> new ResourceNotFoundException("No user with that email")
            );
            itemListPersonRepository.addPerson(
                    conn, sessionId, itemListId, memberPersonId, ItemListMember.Role.MEMBER
            );

            return itemListPersonRepository.getMembers(conn, itemListId).stream()
                    .filter(member -> member.personId() == memberPersonId)
                    .findFirst()
                    .orElseThrow();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The owner can remove anyone else, and a member can remove themselves to
    // leave the list. The owner cannot leave; they delete the list instead.
    public void removeItemListMember(Session session, int itemListId, int memberPersonId)
            throws ResourceNotFoundException, NoAccessToResourceException {
        UUID sessionId = session.getSessionId();
        try (Connection conn = db.getConnection()) {
            User user = authenticationRepository.getUserFromSession(conn, sessionId).orElseThrow(
                    () -> new UserNotLoggedInException("Not a valid session id")
            );
            Integer personId = user.getPerson().getId().orElseThrow(
                    () -> new RuntimeException("Cant find person id connected to session" + sessionId)
            );

            ItemListMember.Role role = requireRole(conn, itemListId, personId, ItemListMember.Role.MEMBER);

            boolean leaving = memberPersonId == personId;
            boolean allowed = leaving
                    ? role == ItemListMember.Role.MEMBER
                    : role == ItemListMember.Role.OWNER;
            if (!allowed) {
                throw new NoAccessToResourceException("Cant remove that member from the list");
            }

            int removed = itemListPersonRepository.removePerson(conn, itemListId, memberPersonId);
            if (0 == removed) {
                throw new ResourceNotFoundException("Not a member of the list");
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private ItemList getItemListWithMembers(Connection conn, int itemListId, int personId) {
        ItemList itemList = itemRepository.getItemList(conn, itemListId).orElseThrow(
                () -> new RuntimeException("Cant find item list with id " + itemListId)
        );
        itemList.setRole(itemListPersonRepository.getRole(conn, itemListId, personId).orElse(null));
        itemList.getMembers().addAll(itemListPersonRepository.getMembers(conn, itemListId));
        return itemList;
    }

    // Returns the person's role in the list. Throws unless the person has at
    // least the given role; MEMBER lets owners through as well.
    private ItemListMember.Role requireRole(
            Connection conn, int itemListId, int personId, ItemListMember.Role required
    ) throws ResourceNotFoundException, NoAccessToResourceException {
        ItemListMember.Role role = itemListPersonRepository.getRole(conn, itemListId, personId).orElseThrow(
                () -> new ResourceNotFoundException("Item list not found")
        );
        if (required == ItemListMember.Role.OWNER && role != ItemListMember.Role.OWNER) {
            throw new NoAccessToResourceException("Only the owner can do this");
        }
        return role;
    }
}
