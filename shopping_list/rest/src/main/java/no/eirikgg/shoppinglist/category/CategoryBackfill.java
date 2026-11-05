package no.eirikgg.shoppinglist.category;

import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository.UnsortedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

// Sorts the items that have no section yet: those from before sections
// existed, and those added while the model was loading. If the catalogue or
// the scoring has changed since the items were sorted, every item is sorted
// again first. The web app shows the sections at its next refresh.
public final class CategoryBackfill {
    private static final Logger log = LoggerFactory.getLogger(CategoryBackfill.class);

    // In app_state: E5Categoriser.version() the items were last sorted with.
    static final String VERSION_KEY = "category.version";

    private final Db db;
    private final Categoriser categoriser;
    private final String version;
    private final ItemRepository itemRepository = new ItemRepository();

    // version is what the categoriser sorts by, as E5Categoriser.version().
    public CategoryBackfill(Db db, Categoriser categoriser, String version) {
        this.db = db;
        this.categoriser = categoriser;
        this.version = version;
    }

    // How many items were sorted.
    public int run() {
        List<UnsortedItem> items;
        try (Connection conn = db.getConnection()) {
            if (!itemRepository.getAppState(conn, VERSION_KEY).map(version::equals).orElse(false)) {
                log.info("The section catalogue has changed, so every item is sorted again");
                conn.setAutoCommit(false);
                itemRepository.clearCategories(conn);
                itemRepository.setAppState(conn, VERSION_KEY, version);
                conn.commit();
                conn.setAutoCommit(true);
            }
            items = itemRepository.getUnsortedItems(conn);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        int sorted = 0;
        for (UnsortedItem item : items) {
            Optional<Category> category = categoriser.categorise(item.name());
            if (category.isEmpty()) {
                continue;
            }
            // One connection per item, so the app is never kept waiting.
            try (Connection conn = db.getConnection()) {
                itemRepository.setCategory(conn, item.id(), item.name(), category.get().key());
                sorted++;
            }
            catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        if (sorted > 0) {
            log.info("Sorted {} items into sections", sorted);
        }
        return sorted;
    }
}
