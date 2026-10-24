--liquibase formatted sql

--changeset egg:1
-- An item can be bought in more than one store.
ALTER TABLE item_store_mapping
    DROP CONSTRAINT item_store_mapping_unique_item_id;

--changeset egg:2
ALTER TABLE item_store_mapping
    ADD CONSTRAINT item_store_mapping_unique_item_id_store_id UNIQUE (item_id, store_id);
