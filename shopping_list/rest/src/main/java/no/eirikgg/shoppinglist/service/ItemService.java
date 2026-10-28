package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.db.repository.StoreRepository;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.errors.InvalidInputException;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import no.eirikgg.shoppinglist.service.ListChanges.ItemEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Every Home Assistant user sees and changes every list, as one household. The
// person is only recorded as who created a list or an item. Every change is
// passed on to changes once it is saved.
public class ItemService {
    public static final int MAX_AMOUNT = 999;

    private Db db;
    private ListChanges changes;

    private ItemRepository itemRepository = new ItemRepository();
    private StoreRepository storeRepository = new StoreRepository();

    public ItemService(Db db) {
        this(db, ListChanges.NONE);
    }

    public ItemService(Db db, ListChanges changes) {
        this.db = db;
        this.changes = changes;
    }

    public ItemList createItemList(Person person, String name) {
        try (Connection conn = db.getConnection()) {
            int itemListId = itemRepository.createItemList(conn, person.id(), name);
            ItemList itemList = itemRepository.getItemList(conn, itemListId).orElseThrow(
                    () -> new RuntimeException("Cant find item list with id " + itemListId)
            );
            changes.listsChanged();
            return itemList;
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

    public Optional<ItemList> getItemList(int itemListId) {
        try (Connection conn = db.getConnection()) {
            return itemRepository.getItemList(conn, itemListId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Lists that were deleted, so what was published about them can be removed.
    public List<Integer> getDeletedItemListIds() {
        try (Connection conn = db.getConnection()) {
            return itemRepository.getDeletedItemListIds(conn);
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
            changes.listsChanged();
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
            changes.listsChanged();
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

    // storeIds may be null or empty, and amount null for 1. The item is
    // connected to the stores in the same transaction, so an item is never
    // created without its stores.
    public Item createItem(Person person, int itemListId, String name, Integer amount, List<Integer> storeIds)
            throws ResourceNotFoundException, NoAccessToResourceException, InvalidInputException {
        int newAmount = null == amount ? 1 : validAmount(amount);
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

            Item item;
            try {
                conn.setAutoCommit(false);
                int itemId = itemRepository.createItem(conn, person.id(), itemListId, name, newAmount);
                for (int storeId : stores) {
                    storeRepository.mapStoreToItem(conn, storeId, itemId);
                }
                conn.commit();
                item = itemRepository.getItem(conn, itemId).orElseThrow();
            }
            catch (Exception e) {
                conn.rollback();
                throw e;
            }
            changes.itemChanged(ItemEvent.ITEM_ADDED, itemListId, item, person);
            return item;
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Deleting an item that is already gone changes nothing.
    public void deleteItem(Person person, int itemId) {
        try (Connection conn = db.getConnection()) {
            Optional<Item> item = itemRepository.getItem(conn, itemId);
            Optional<Integer> itemListId = itemRepository.getItemListIdOfItem(conn, itemId);
            itemRepository.deleteItem(conn, itemId);
            if (item.isPresent() && itemListId.isPresent()) {
                changes.itemChanged(ItemEvent.ITEM_DELETED, itemListId.get(), item.get(), person);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // amount may be null, which keeps the item's amount. Completing an item
    // and changing it in the same call is passed on as both.
    public Item patchItem(Person person, int itemId, String newName, Integer amount, LocalDateTime completed)
            throws ResourceNotFoundException, InvalidInputException {
        if (null != amount) {
            validAmount(amount);
        }
        try (Connection conn = db.getConnection()) {
            Item before = itemRepository.getItem(conn, itemId).orElseThrow(
                    () -> new ResourceNotFoundException("Item not found")
            );
            int itemListId = itemRepository.getItemListIdOfItem(conn, itemId).orElseThrow();
            int newAmount = null == amount ? before.getAmount() : amount;
            itemRepository.patchItem(conn, itemId, newName, newAmount, completed);
            Item after = itemRepository.getItem(conn, itemId).orElseThrow();

            if (before.getCompleted().isEmpty() && after.getCompleted().isPresent()) {
                changes.itemChanged(ItemEvent.ITEM_COMPLETED, itemListId, after, person);
            }
            else if (before.getCompleted().isPresent() && after.getCompleted().isEmpty()) {
                changes.itemChanged(ItemEvent.ITEM_REOPENED, itemListId, after, person);
            }
            if (!Objects.equals(before.getName(), after.getName()) || before.getAmount() != after.getAmount()) {
                changes.itemChanged(ItemEvent.ITEM_CHANGED, itemListId, after, person);
            }
            return after;
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static int validAmount(int amount) throws InvalidInputException {
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new InvalidInputException(Error.INVALID_AMOUNT, "Amount must be from 1 to " + MAX_AMOUNT);
        }
        return amount;
    }
}
