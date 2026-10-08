package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.User;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.AuthenticationRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.errors.UserNotLoggedInException;

import java.sql.Connection;
import java.sql.SQLException;
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

    public ItemList createItemList(UUID sessionId, String name) {
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

    public List<ItemList> getItemLists(UUID sessionId) {
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
}
