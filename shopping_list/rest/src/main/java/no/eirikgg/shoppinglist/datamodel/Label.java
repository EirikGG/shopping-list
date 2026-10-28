package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;

// A label belongs to a list, and everyone with access to the list shares it.
// personId is only who created it.
public class Label extends DbIdentity {

    private int personId;
    private int itemListId;
    private String name;
    private LocalDateTime created;

    public Label(int labelId, int personId, int itemListId, String name, LocalDateTime created) {
        super(labelId);
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
