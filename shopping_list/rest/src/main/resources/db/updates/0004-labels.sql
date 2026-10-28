--liquibase formatted sql

--changeset egg:1
-- Stores are now labels, which also suit lists that are not for shopping.
-- Renamed in place, so every label keeps its id and its items. SQLite updates
-- the references to the renamed tables and columns by itself.
ALTER TABLE store RENAME TO label;
ALTER TABLE label RENAME COLUMN store_id TO label_id;
ALTER TABLE item_store_mapping RENAME TO item_label_mapping;
ALTER TABLE item_label_mapping RENAME COLUMN store_id TO label_id;

-- Indexes can not be renamed, so they are made again.
DROP INDEX ix_store_list_name_active_unique;
CREATE UNIQUE INDEX ix_label_list_name_active_unique
    ON label (item_list_id, name)
    WHERE deleted IS NULL;

DROP INDEX idx_item_store_mapping_store_id;
CREATE INDEX idx_item_label_mapping_label_id ON item_label_mapping (label_id);
