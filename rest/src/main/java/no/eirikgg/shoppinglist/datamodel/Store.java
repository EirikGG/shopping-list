package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;

public class Store extends DbIdentity {

    private int personId;
    private String name;
    private LocalDateTime created;

    public Store(int storeId, int personId, String name, LocalDateTime created) {
        super(storeId);
        this.personId = personId;
        this.name = name;
        this.created = created;
    }

    public int getPersonId() {
        return personId;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreated() {
        return created;
    }
}
