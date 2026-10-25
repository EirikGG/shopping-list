--liquibase formatted sql

-- Stores move from belonging to a person to belonging to a list, so everyone
-- with access to a list shares its stores. store.person_id stays as the person
-- who created the store; access is decided by the list.

--changeset egg:1
ALTER TABLE store ADD COLUMN item_list_id INTEGER NULL REFERENCES item_list (item_list_id);

--changeset egg:2
-- Names were unique per person. They become unique per list (changeset 6);
-- the old rule would block the copies below.
DROP INDEX ix_store_person_name_active_unique;

--changeset egg:3
-- Each active store is copied into the lists that should have it: every list
-- its creator owns, and every list where an item uses it. Two stores with the
-- same name landing in one list become one.
INSERT INTO store (session_id, person_id, name, created, item_list_id)
SELECT DISTINCT ON (target.item_list_id, s.name)
    s.session_id, s.person_id, s.name, s.created, target.item_list_id
FROM store AS s
JOIN (
    SELECT s2.store_id, ilp.item_list_id
    FROM store AS s2
    JOIN item_list_person AS ilp
        ON ilp.person_id = s2.person_id
        AND ilp.role = 'OWNER'
    UNION
    SELECT m.store_id, i.item_list_id
    FROM item_store_mapping AS m
    JOIN item AS i
        ON i.item_id = m.item_id
) AS target
    ON target.store_id = s.store_id
JOIN item_list AS il
    ON il.item_list_id = target.item_list_id
    AND il.deleted IS NULL
WHERE s.deleted IS NULL
    AND s.item_list_id IS NULL
ORDER BY target.item_list_id, s.name, s.created, s.store_id;

--changeset egg:4
-- Items point to their list's copy of the store instead of the old one.
INSERT INTO item_store_mapping (session_id, store_id, item_id)
SELECT m.session_id, copy.store_id, m.item_id
FROM item_store_mapping AS m
JOIN store AS old
    ON old.store_id = m.store_id
    AND old.item_list_id IS NULL
    AND old.deleted IS NULL
JOIN item AS i
    ON i.item_id = m.item_id
JOIN store AS copy
    ON copy.item_list_id = i.item_list_id
    AND copy.name = old.name
    AND copy.deleted IS NULL
ON CONFLICT (item_id, store_id) DO NOTHING;

DELETE FROM item_store_mapping AS m
USING store AS s
WHERE s.store_id = m.store_id
    AND s.item_list_id IS NULL;

--changeset egg:5
-- The old person-level stores are kept for history but no longer used.
UPDATE store
SET deleted = now()
WHERE item_list_id IS NULL
    AND deleted IS NULL;

--changeset egg:6
CREATE UNIQUE INDEX ix_store_list_name_active_unique
    ON store (item_list_id, name)
    WHERE deleted IS NULL;

CREATE INDEX idx_store_item_list_id ON store (item_list_id);

ALTER TABLE store
    ADD CONSTRAINT store_active_has_list CHECK (deleted IS NOT NULL OR item_list_id IS NOT NULL);
