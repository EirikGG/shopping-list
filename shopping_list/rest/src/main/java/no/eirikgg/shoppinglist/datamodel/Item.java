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
    // A Category key, or null until the item has been sorted.
    private String category;
    private List<Label> labels;
    private final List<ItemLink> links = new LinkedList<>();
    private final List<Attachment> attachments = new LinkedList<>();

    public Item(
            Integer id, String name, int amount, LocalDateTime created, LocalDateTime completed, String category,
            List<Label> labels
    ) {
        super(id);
        this.name = name;
        this.amount = amount;
        this.created = created;
        this.completed = completed;
        this.category = category;
        this.labels = labels;
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

    public List<Label> getLabels() {
        if (null == labels) {
            labels = new LinkedList<>();
        }
        return labels;
    }

    public List<ItemLink> getLinks() {
        return links;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public Optional<String> getCategory() {
        return Optional.ofNullable(category);
    }

    public Optional<LocalDateTime> getCompleted() {
        return Optional.ofNullable(completed);
    }
}
