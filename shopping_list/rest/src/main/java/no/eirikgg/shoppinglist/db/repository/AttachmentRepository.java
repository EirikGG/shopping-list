package no.eirikgg.shoppinglist.db.repository;

import no.eirikgg.shoppinglist.datamodel.Attachment;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.RSWrapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// The files themselves are handled by AttachmentService. Access is checked by
// the caller, through the item.
public class AttachmentRepository {
    private DbUtils dbUtils = new DbUtils();

    public int createAttachment(
            Connection conn, int personId, int itemId,
            String fileName, String contentType, long size, String storageKey
    ) throws SQLException {
        return dbUtils.insert(conn, """
                insert into attachment (
                    item_id,
                    person_id,
                    file_name,
                    content_type,
                    size,
                    storage_key,
                    created
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                itemId,
                personId,
                fileName,
                contentType,
                size,
                storageKey,
                LocalDateTime.now()
        ).orElseThrow();
    }

    // In the order they were added.
    public List<Attachment> getAttachmentsForItem(Connection conn, int itemId) {
        return dbUtils.select(conn, """
                select attachment_id, item_id, file_name, content_type, size, storage_key, created
                from attachment
                where item_id = ?
                and deleted is null
                order by attachment_id
                """,
                itemId,
                AttachmentRepository::toAttachment
        );
    }

    // Empty if the attachment, its item or its list is deleted.
    public Optional<Attachment> getAttachment(Connection conn, int attachmentId) {
        return dbUtils.selectSingle(conn, """
                select a.attachment_id, a.item_id, a.file_name, a.content_type, a.size, a.storage_key, a.created
                from attachment as a
                join item as i
                    on i.item_id = a.item_id
                    and i.deleted is null
                join item_list as il
                    on il.item_list_id = i.item_list_id
                    and il.deleted is null
                where a.attachment_id = ?
                and a.deleted is null
                """,
                attachmentId,
                AttachmentRepository::toAttachment
        );
    }

    public void deleteAttachment(Connection conn, int attachmentId) throws SQLException {
        dbUtils.update(conn, """
                update attachment
                set deleted = ?
                where attachment_id = ?
                """,
                LocalDateTime.now(),
                attachmentId
        );
    }

    // The files still in use: those of attachments on items and lists that
    // are not deleted. Any other file in the folder can go.
    public Set<String> getStorageKeysInUse(Connection conn) {
        return dbUtils.select(conn, """
                select a.storage_key
                from attachment as a
                join item as i
                    on i.item_id = a.item_id
                    and i.deleted is null
                join item_list as il
                    on il.item_list_id = i.item_list_id
                    and il.deleted is null
                where a.deleted is null
                """,
                new Object[] {},
                rs -> rs.getString("storage_key").orElseThrow()
        ).stream().collect(Collectors.toSet());
    }

    private static Attachment toAttachment(RSWrapper rs) {
        return new Attachment(
                rs.getInt("attachment_id").orElseThrow(),
                rs.getInt("item_id").orElseThrow(),
                rs.getString("file_name").orElseThrow(),
                rs.getString("content_type").orElseThrow(),
                rs.getInt("size").orElseThrow(),
                rs.getString("storage_key").orElseThrow(),
                rs.getLocalDateTime("created").orElseThrow()
        );
    }
}
