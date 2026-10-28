package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.Label;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.db.repository.LabelRepository;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// Labels belong to a list, and can only be used on that list's items. Like the
// lists themselves, every user can see and change them.
public class LabelService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;
    private ListChanges changes;
    private LabelRepository labelRepository = new LabelRepository();
    private ItemRepository itemRepository = new ItemRepository();

    public LabelService(Db db) {
        this(db, ListChanges.NONE);
    }

    // Giving an item a label, or taking one away, is passed on to changes.
    public LabelService(Db db, ListChanges changes) {
        this.db = db;
        this.changes = changes;
    }

    public Label createLabel(Person person, int itemListId, String name) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            requireList(conn, itemListId);

            int labelId = labelRepository.createLabel(conn, person.id(), itemListId, name);
            return labelRepository.getLabel(conn, labelId).orElseThrow(
                    () -> new RuntimeException("Failed to create label")
            );
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Label getLabel(int labelId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            return existingLabel(conn, labelId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The labels of a list, sorted by name.
    public List<Label> getLabels(int itemListId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            requireList(conn, itemListId);
            return labelRepository.getLabelsForList(conn, itemListId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void patchLabel(int labelId, String newName) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            existingLabel(conn, labelId);
            labelRepository.updateLabel(conn, labelId, newName);
            // The items show the new name.
            changes.listsChanged();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteLabel(int labelId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            existingLabel(conn, labelId);
            labelRepository.deleteLabel(conn, labelId);
            changes.listsChanged();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The label must be on the same list as the item.
    public void mapLabelAndItem(Person person, int labelId, int itemId)
            throws ResourceNotFoundException, NoAccessToResourceException {
        try (Connection conn = db.getConnection()) {
            Label label = existingLabel(conn, labelId);
            int itemListId = itemListIdOfItem(conn, itemId);
            if (label.getItemListId() != itemListId) {
                throw new NoAccessToResourceException("Label is not on the item's list");
            }

            if (labelRepository.mapLabelToItem(conn, labelId, itemId)) {
                itemLabelsChanged(conn, person, itemListId, itemId);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void unmapLabelFromItem(Person person, int labelId, int itemId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            int itemListId = itemListIdOfItem(conn, itemId);
            if (labelRepository.unmapLabelFromItem(conn, labelId, itemId)) {
                itemLabelsChanged(conn, person, itemListId, itemId);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void itemLabelsChanged(Connection conn, Person person, int itemListId, int itemId) {
        itemRepository.getItem(conn, itemId).ifPresent(
                item -> changes.itemChanged(ListChanges.ItemEvent.ITEM_CHANGED, itemListId, item, person)
        );
    }

    // A label on a deleted list is reported as not found.
    private Label existingLabel(Connection conn, int labelId) throws ResourceNotFoundException {
        Label label = labelRepository.getLabel(conn, labelId).orElseThrow(
                () -> new ResourceNotFoundException("Label not found")
        );
        requireList(conn, label.getItemListId());
        return label;
    }

    private int itemListIdOfItem(Connection conn, int itemId) throws ResourceNotFoundException {
        if (itemRepository.getItem(conn, itemId).isEmpty()) {
            throw new ResourceNotFoundException("Item not found");
        }
        return itemRepository.getItemListIdOfItem(conn, itemId).orElseThrow(
                () -> new ResourceNotFoundException("Item not found")
        );
    }

    private void requireList(Connection conn, int itemListId) throws ResourceNotFoundException {
        if (!itemRepository.itemListExists(conn, itemListId)) {
            throw new ResourceNotFoundException("Item list not found");
        }
    }
}
