package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;
import java.util.Optional;

public class Item extends DbIdentity {

    private String name;
    private LocalDateTime completed;

    public Item(Integer id, String name, LocalDateTime completed) {
        super(id);
        this.name = name;
        this.completed = completed;
    }

    public Item(String name, LocalDateTime completed) {
        this(null, name, completed);
    }

    public String getName() {
        return name;
    }

    public Optional<LocalDateTime> getCompleted() {
        return Optional.ofNullable(completed);
    }
}
