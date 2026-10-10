--liquibase formatted sql

--changeset egg:1
insert into item_list (
    session_id,
    person_id,
    name,
    created,
    deleted
) values (
    '00000000-0000-0000-0000-000000000001',
    (select person_id from person where firstname = 'Ola' and lastname = 'Normann'),
    'unit-test-list',
    now(),
    null
);