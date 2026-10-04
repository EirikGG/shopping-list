--liquibase formatted sql

--changeset egg:1
alter table person_login add constraint uq_person_login_email unique (email);