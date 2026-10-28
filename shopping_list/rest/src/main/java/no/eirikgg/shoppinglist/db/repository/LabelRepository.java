package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Label;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.RSWrapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Labels belong to a list. Access is checked by the caller, through the list.
public class LabelRepository {

    private DbUtils dbUtils = new DbUtils();

    public int createLabel(
            Connection conn, int personId, int itemListId, String name
    ) throws SQLException {
        return dbUtils.insert(conn, """
                insert into label (
                    person_id,
                    item_list_id,
                    name,
                    created
                ) values (?, ?, ?, ?)
                """,
                personId,
                itemListId,
                name,
                LocalDateTime.now()
        ).orElseThrow();
    }

    public Optional<Label> getLabel(Connection conn, int labelId) {
        return dbUtils.selectSingle(conn, """
                select
                    label_id,
                    person_id,
                    item_list_id,
                    name,
                    created
                from label
                where label_id = ?
                and deleted is null
                """,
                labelId,
                LabelRepository::toLabel
        );
    }

    // The labels of a list, sorted by name.
    public List<Label> getLabelsForList(Connection conn, int itemListId) {
        return dbUtils.select(conn, """
                select
                    label_id,
                    person_id,
                    item_list_id,
                    name,
                    created
                from label
                where item_list_id = ?
                and deleted is null
                order by lower(name), label_id
                """,
                itemListId,
                LabelRepository::toLabel
        );
    }

    // Reads label_id, person_id, item_list_id, name and created.
    static Label toLabel(RSWrapper rs) {
        return new Label(
                rs.getInt("label_id").orElseThrow(),
                rs.getInt("person_id").orElseThrow(),
                rs.getInt("item_list_id").orElseThrow(),
                rs.getString("name").orElseThrow(),
                rs.getLocalDateTime("created").orElseThrow()
        );
    }

    public void updateLabel(Connection conn, int labelId, String newName) throws SQLException {
        dbUtils.update(conn, """
            update label
            set name = ?
            where label_id = ?
            """,
            newName,
            labelId
        );
    }

    public void deleteLabel(Connection conn, int labelId) throws SQLException {
        dbUtils.update(conn, """
            update label
            set deleted = ?
            where label_id = ?
            """,
            LocalDateTime.now(),
            labelId
        );
    }

    // Mapping an item to a label it already has does nothing. True if the
    // item got the label.
    public boolean mapLabelToItem(Connection conn, int labelId, int itemId) throws SQLException {
        return dbUtils.update(conn, """
            insert into item_label_mapping (
                 item_id,
                 label_id
            ) values (?, ?)
            on conflict (item_id, label_id) do nothing
            """,
            itemId,
            labelId
        ).orElse(0) > 0;
    }

    // True if the item had the label.
    public boolean unmapLabelFromItem(Connection conn, int labelId, int itemId) throws SQLException {
        return dbUtils.update(conn, """
            delete from item_label_mapping
            where label_id = ?
            and item_id = ?
            """,
            labelId,
            itemId
        ).orElse(0) > 0;
    }
}
