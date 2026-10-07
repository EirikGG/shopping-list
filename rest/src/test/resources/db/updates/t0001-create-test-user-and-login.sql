--liquibase formatted sql

--changeset egg:1
insert into person (
    firstname,
    lastname,
    birthdate,
    gender,
    created,
    deleted
) values (
    'Ola',
    'Normann',
    '1994-05-14',
    'MALE',
    now(),
    '9999-12-31 23:59:59'
);

--changeset egg:2
-- password: unit-test-password
insert into person_login (
    person_id,
    email,
    password,
    created,
    deleted,
    verification_code,
    verified_at
) values (
    (select person_id from person where firstname = 'Ola' and lastname = 'Normann'),
    'unit-test-user@test.com',
    'pbkdf2-sha256$600000$HVKpPgs5k01rGOKXvlIpbQ==$osew0K/DREeBpTc0H1J8ewPhS2EOM+D2sZixVA6Y1AQ=',
    now(),
    '9999-12-31 23:59:59',
    12345,
    now()
);

--changeset egg:3
insert into session (
    session_id,
    person_login_id,
    valid_from,
    valid_until
) values (
    '00000000-0000-0000-0000-000000000001',
    (select person_login_id from person_login where email = 'unit-test-user@test.com'),
    now(),
    '9999-12-31 23:59:59'
);
