--liquibase formatted sql

--changeset egg:1
-- Links on an item, e.g. to a recipe or a product page. Only http and https.
CREATE TABLE item_link (
    item_link_id    INTEGER PRIMARY KEY
    , item_id       INTEGER NOT NULL REFERENCES item (item_id)
    , person_id     INTEGER NOT NULL REFERENCES person (person_id)
    , url           TEXT NOT NULL CHECK (length(url) <= 2048)
    , title         TEXT NULL CHECK (length(title) <= 256)
    , created       TEXT NOT NULL
    , deleted       TEXT NULL
);

CREATE INDEX idx_item_link_item_id ON item_link (item_id);

--changeset egg:2
-- Files and images on an item. The file itself is in the files folder under
-- storage_key, a random name; file_name is the name it was uploaded with.
CREATE TABLE attachment (
    attachment_id   INTEGER PRIMARY KEY
    , item_id       INTEGER NOT NULL REFERENCES item (item_id)
    , person_id     INTEGER NOT NULL REFERENCES person (person_id)
    , file_name     TEXT NOT NULL CHECK (length(file_name) <= 256)
    , content_type  TEXT NOT NULL CHECK (length(content_type) <= 256)
    , size          INTEGER NOT NULL
    , storage_key   TEXT NOT NULL UNIQUE
    , created       TEXT NOT NULL
    , deleted       TEXT NULL
);

CREATE INDEX idx_attachment_item_id ON attachment (item_id);
