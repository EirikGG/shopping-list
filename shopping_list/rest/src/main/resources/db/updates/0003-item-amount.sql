--liquibase formatted sql

--changeset egg:1
-- How many of the item to buy. Items added before this get 1.
ALTER TABLE item ADD COLUMN amount INTEGER NOT NULL DEFAULT 1 CHECK (amount BETWEEN 1 AND 999);
