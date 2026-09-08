--liquibase formatted sql

--changeset egg:1
create table session
(
    session_id          UUID primary key
    , person_login_id   INTEGER not null references person_login (person_login_id)
    , valid_from        timestamp not null
    , valid_until       timestamp not null
);

