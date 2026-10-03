--liquibase formatted sql

--changeset egg:1
alter table person_login
    add column verification_code integer not null
    default (floor(random() * 90000 + 10000))::integer;

--changeset egg:2
alter table person_login
    add column verified_at timestamp null;