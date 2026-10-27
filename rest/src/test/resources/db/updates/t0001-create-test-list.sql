--liquibase formatted sql

--changeset egg:1
-- A list someone else created, which the test users see like their own.
insert into person (
    ha_user_id,
    name,
    created
) values (
    'fixture-ha-user',
    'Fixture User',
    '2026-01-01T00:00:00.000'
);

insert into item_list (
    person_id,
    name,
    created
) values (
    (select person_id from person where ha_user_id = 'fixture-ha-user'),
    'unit-test-list',
    '2026-01-01T00:00:00.000'
);
