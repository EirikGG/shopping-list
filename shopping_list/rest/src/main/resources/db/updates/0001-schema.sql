--liquibase formatted sql

-- SQLite. Timestamps are ISO-8601 text (2026-10-27T17:10:57.432), written and
-- read by DbUtils and RSWrapper, so they sort and compare as text. Rows are
-- deleted by setting deleted, and kept.

--changeset egg:1
-- A Home Assistant user who has opened the app. ha_user_id is HA's id for the
-- user, which never changes; name is their display name, which can.
CREATE TABLE person (
    person_id       INTEGER PRIMARY KEY
    , ha_user_id    TEXT NOT NULL UNIQUE
    , name          TEXT NULL
    , created       TEXT NOT NULL
);

--changeset egg:2
-- Every user sees every list. person_id is only who created it.
CREATE TABLE item_list (
    item_list_id    INTEGER PRIMARY KEY
    , person_id     INTEGER NOT NULL REFERENCES person (person_id)
    , name          TEXT NOT NULL CHECK (length(name) <= 512)
    , created       TEXT NOT NULL
    , deleted       TEXT NULL
);

--changeset egg:3
CREATE TABLE item (
    item_id         INTEGER PRIMARY KEY
    , item_list_id  INTEGER NOT NULL REFERENCES item_list (item_list_id)
    , person_id     INTEGER NOT NULL REFERENCES person (person_id)
    , name          TEXT NOT NULL CHECK (length(name) <= 512)
    , created       TEXT NOT NULL
    , completed     TEXT NULL
    , deleted       TEXT NULL
);

CREATE INDEX idx_item_item_list_id ON item (item_list_id);

--changeset egg:4
-- A store belongs to a list, and can only be used on that list's items.
CREATE TABLE store (
    store_id        INTEGER PRIMARY KEY
    , item_list_id  INTEGER NOT NULL REFERENCES item_list (item_list_id)
    , person_id     INTEGER NOT NULL REFERENCES person (person_id)
    , name          TEXT NOT NULL CHECK (length(name) <= 256)
    , created       TEXT NOT NULL
    , deleted       TEXT NULL
);

CREATE UNIQUE INDEX ix_store_list_name_active_unique
    ON store (item_list_id, name)
    WHERE deleted IS NULL;

--changeset egg:5
-- An item can be bought in more than one store.
CREATE TABLE item_store_mapping (
    item_id         INTEGER NOT NULL REFERENCES item (item_id)
    , store_id      INTEGER NOT NULL REFERENCES store (store_id)

    , PRIMARY KEY (item_id, store_id)
);

CREATE INDEX idx_item_store_mapping_store_id ON item_store_mapping (store_id);
