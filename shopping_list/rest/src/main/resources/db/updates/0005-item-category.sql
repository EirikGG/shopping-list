--liquibase formatted sql

--changeset egg:1
-- The store section the item was sorted into, as a key like 'dairy', or
-- 'uncategorised' when no section fit. NULL until it has been sorted, which
-- is done when the item is added or renamed, or by CategoryBackfill.
ALTER TABLE item ADD COLUMN category TEXT NULL;
