--liquibase formatted sql

--changeset egg:1
-- Who has access to a list. A list is shared by adding more persons to it.
-- item_list.person_id is kept as the person who created the list, but access
-- is only ever decided here.
CREATE TABLE item_list_person (
    item_list_id    INTEGER NOT NULL REFERENCES item_list (item_list_id)
    , person_id     INTEGER NOT NULL REFERENCES person (person_id)
    , role          VARCHAR(16) NOT NULL
    , session_id    UUID NOT NULL REFERENCES session (session_id)
    , created       TIMESTAMP NOT NULL

    , PRIMARY KEY (item_list_id, person_id)
    , CHECK (role IN ('OWNER', 'MEMBER'))
);

--changeset egg:2
CREATE INDEX idx_item_list_person_person_id ON item_list_person (person_id);

--changeset egg:3
-- Every existing list is owned by the person who created it.
INSERT INTO item_list_person (item_list_id, person_id, role, session_id, created)
SELECT item_list_id, person_id, 'OWNER', session_id, created
FROM item_list;
