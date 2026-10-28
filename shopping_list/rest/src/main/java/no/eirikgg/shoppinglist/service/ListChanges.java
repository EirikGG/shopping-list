package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.Person;

// Told about every change to the lists, after it is saved. The MQTT bridge
// passes them on to Home Assistant. Called on the thread that made the change,
// so an implementation must not block.
public interface ListChanges {

    // What happened to an item. The names are what Home Assistant sees.
    enum ItemEvent {
        ITEM_ADDED("item_added"),
        ITEM_COMPLETED("item_completed"),
        ITEM_REOPENED("item_reopened"),
        // A new name, amount or stores.
        ITEM_CHANGED("item_changed"),
        ITEM_DELETED("item_deleted");

        private final String eventType;

        ItemEvent(String eventType) {
            this.eventType = eventType;
        }

        public String eventType() {
            return eventType;
        }
    }

    // item is as it is after the change, or before it for ITEM_DELETED. by is
    // who made the change.
    void itemChanged(ItemEvent event, int itemListId, Item item, Person by);

    // A list was created, renamed or deleted, or a store renamed or deleted.
    void listsChanged();

    // For when nothing listens, as in most tests.
    ListChanges NONE = new ListChanges() {
        @Override
        public void itemChanged(ItemEvent event, int itemListId, Item item, Person by) {}

        @Override
        public void listsChanged() {}
    };
}
