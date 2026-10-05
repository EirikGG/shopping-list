package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.AuthenticationRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

public class ItemService {
    private Db db;

    private ItemRepository itemRepository = new ItemRepository();
    private AuthenticationRepository authenticationRepository = new AuthenticationRepository();

    public ItemService(Db db) {
        this.db = db;
    }

    public void createItemList(UUID sessionId, String name) {
        try (Connection conn = db.getConnection()) {
            itemRepository.createItemList(conn, sessionId, name);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
