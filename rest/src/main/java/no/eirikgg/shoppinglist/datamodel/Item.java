package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class Item extends DbIdentity {

    private String name;
    private LocalDateTime completed;
    private List<Store> stores;

    public Item(Integer id, String name, LocalDateTime completed) {
        this(id, name, completed, null);
    }

    public Item(Integer id, String name, LocalDateTime completed, List<Store> stores) {
        super(id);
        this.name = name;
        this.completed = completed;
        this.stores = stores;
    }

    public String getName() {
        return name;
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
