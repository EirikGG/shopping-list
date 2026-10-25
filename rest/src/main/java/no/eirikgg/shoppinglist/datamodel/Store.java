package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;

// A store belongs to a list, and everyone with access to the list shares it.
// personId is only who created it.
public class Store extends DbIdentity {

    private int personId;
    private int itemListId;
    private String name;
    private LocalDateTime created;

    public Store(int storeId, int personId, int itemListId, String name, LocalDateTime created) {
        super(storeId);
        this.personId = personId;
        this.itemListId = itemListId;
        this.name = name;
        this.created = created;
    }

    public int getPersonId() {
        return personId;
    }

    public int getItemListId() {
        return itemListId;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreated() {
        return created;
    }
}
