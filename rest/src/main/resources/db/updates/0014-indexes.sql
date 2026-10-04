--liquibase formatted sql

--changeset egg:1
create index idx_email_message_unsent on email_message (email_id) where sent is null;

--changeset egg:2
create index idx_person_login_person_id on person_login (person_id);

--changeset egg:3
create index idx_session_person_login_id on session (person_login_id);

--changeset egg:4
create index idx_email_message_session_id on email_message (session_id);

--changeset egg:5
create index idx_item_session_id on item (session_id);

--changeset egg:6
create index idx_item_list_session_id on item_list (session_id);

--changeset egg:7
create index idx_item_list_item_id on item_list (item_id);
