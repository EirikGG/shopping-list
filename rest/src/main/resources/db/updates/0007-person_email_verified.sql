--liquibase formatted sql

--changeset egg:1
alter table person_email ADD column verified timestamp null;

--changeset egg:2
ALTER TABLE person_email ADD COLUMN verification_uuid UUID DEFAULT gen_random_uuid();
ALTER TABLE person_email ALTER COLUMN verification_uuid SET NOT NULL;
ALTER TABLE person_email ALTER COLUMN verification_uuid DROP DEFAULT;