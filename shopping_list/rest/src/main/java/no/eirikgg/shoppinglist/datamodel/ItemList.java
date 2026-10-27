package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.util.LinkedList;
import java.util.List;

public class ItemList extends DbIdentity {

    private String name;
    private List<Item> items = new LinkedList<>();


    public ItemList(Integer id, String name) {
        super(id);
        this.name = name;
    }

    public ItemList(String name) {
        this(null, name);
    }

    public String getName() {
        return name;
    }

    public List<Item> getItems() {
        return items;
    }
}
