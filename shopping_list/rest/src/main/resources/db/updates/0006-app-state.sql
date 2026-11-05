--liquibase formatted sql

--changeset egg:1
-- Values the app keeps about itself, such as which catalogue the items' store
-- sections were sorted with, so they are sorted again when it changes.
CREATE TABLE app_state (
    key             TEXT PRIMARY KEY
    , value         TEXT NOT NULL
);
