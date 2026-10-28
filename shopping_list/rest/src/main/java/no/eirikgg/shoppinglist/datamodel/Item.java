package no.eirikgg.shoppinglist.datamodel;

import no.eirikgg.shoppinglist.db.DbIdentity;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class Item extends DbIdentity {

    private String name;
    private int amount;
    private LocalDateTime created;
    private LocalDateTime completed;
    private List<Store> stores;
    private final List<ItemLink> links = new LinkedList<>();
    private final List<Attachment> attachments = new LinkedList<>();

    public Item(Integer id, String name, int amount, LocalDateTime created, LocalDateTime completed, List<Store> stores) {
        super(id);
        this.name = name;
        this.amount = amount;
        this.created = created;
        this.completed = completed;
        this.stores = stores;
    }

    public String getName() {
        return name;
    }

    public int getAmount() {
        return amount;
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

    public List<ItemLink> getLinks() {
        return links;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public Optional<LocalDateTime> getCompleted() {
        return Optional.ofNullable(completed);
    }
}
