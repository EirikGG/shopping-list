--liquibase formatted sql

--changeset egg:1
alter table person_login rename column username to email