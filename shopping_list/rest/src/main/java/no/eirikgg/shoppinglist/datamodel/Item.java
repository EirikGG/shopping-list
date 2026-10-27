package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class Item extends DbIdentity {

    private String name;
    private LocalDateTime created;
    private LocalDateTime completed;
    private List<Store> stores;

    public Item(Integer id, String name, LocalDateTime created, LocalDateTime completed, List<Store> stores) {
        super(id);
        this.name = name;
        this.created = created;
        this.completed = completed;
        this.stores = stores;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public List<Store> getStores() {
        if (null == stores) {
            stores = new LinkedList<>();
        }
        return stores;
    }

    public Optional<LocalDateTime> getCompleted() {
        return Optional.ofNullable(completed);
    }
}
