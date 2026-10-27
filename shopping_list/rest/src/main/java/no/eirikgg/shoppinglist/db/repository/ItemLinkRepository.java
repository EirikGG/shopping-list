package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.ItemLink;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.RSWrapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Access is checked by the caller, through the item.
public class ItemLinkRepository {
    private DbUtils dbUtils = new DbUtils();

    public int createLink(Connection conn, int personId, int itemId, String url, String title) throws SQLException {
        return dbUtils.insert(conn, """
                insert into item_link (
                    item_id,
                    person_id,
                    url,
                    title,
                    created
                ) values (?, ?, ?, ?, ?)
                """,
                itemId,
                personId,
                url,
                title,
                LocalDateTime.now()
        ).orElseThrow();
    }

    // In the order they were added.
    public List<ItemLink> getLinksForItem(Connection conn, int itemId) {
        return dbUtils.select(conn, """
                select item_link_id, item_id, url, title, created
                from item_link
                where item_id = ?
                and deleted is null
                order by item_link_id
                """,
                itemId,
                ItemLinkRepository::toLink
        );
    }

    public Optional<ItemLink> getLink(Connection conn, int linkId) {
        return dbUtils.selectSingle(conn, """
                select item_link_id, item_id, url, title, created
                from item_link
                where item_link_id = ?
                and deleted is null
                """,
                linkId,
                ItemLinkRepository::toLink
        );
    }

    public void deleteLink(Connection conn, int linkId) throws SQLException {
        dbUtils.update(conn, """
                update item_link
                set deleted = ?
                where item_link_id = ?
                """,
                LocalDateTime.now(),
                linkId
        );
    }

    private static ItemLink toLink(RSWrapper rs) {
        return new ItemLink(
                rs.getInt("item_link_id").orElseThrow(),
                rs.getInt("item_id").orElseThrow(),
                rs.getString("url").orElseThrow(),
                rs.getString("title").orElse(null),
                rs.getLocalDateTime("created").orElseThrow()
        );
    }
}
