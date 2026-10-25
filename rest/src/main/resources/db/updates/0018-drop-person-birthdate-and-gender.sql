--liquibase formatted sql

--changeset egg:1
-- The app has no use for them, so they are no longer collected or kept.
-- Dropping gender also drops its check constraint.
ALTER TABLE person DROP COLUMN IF EXISTS birthdate;
ALTER TABLE person DROP COLUMN IF EXISTS gender;
