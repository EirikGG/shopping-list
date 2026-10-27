package no.eirikgg.shoppinglist.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteDbTest {

    private final TestDb db = TestDb.createMigratedInstance();
    private final DbUtils dbUtils = new DbUtils();

    @AfterEach
    void closeDb() {
        db.close();
    }

    // SQLite only checks foreign keys on connections that ask for it, so every
    // connection in the pool is tried, not just the first.
    @Test
    void foreignKeys_areCheckedOnEveryConnection() throws SQLException {
        List<Connection> connections = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                connections.add(db.getConnection());
            }
            for (Connection conn : connections) {
                assertThrows(SQLException.class, () -> dbUtils.insert(conn,
                        "insert into item_list (person_id, name, created) values (?, ?, ?)",
                        999_999, "no such person", LocalDateTime.now()
                ));
            }
        }
        finally {
            for (Connection conn : connections) {
                conn.close();
            }
        }
    }

    @Test
    void timestamps_comeBackAsWritten() throws SQLException {
        LocalDateTime created = LocalDateTime.of(2026, 10, 27, 17, 10, 57, 432_000_000);
        try (Connection conn = db.getConnection()) {
            int personId = dbUtils.insert(conn,
                    "insert into person (ha_user_id, name, created) values (?, ?, ?)",
                    "ha-user", "Name", created
            ).orElseThrow();

            LocalDateTime read = dbUtils.selectSingle(conn,
                    "select created from person where person_id = ?",
                    personId,
                    rs -> rs.getLocalDateTime("created").orElseThrow()
            ).orElseThrow();
            assertEquals(created, read);
        }
    }

    @Test
    void namesLongerThanTheLimit_areRefused() throws SQLException {
        try (Connection conn = db.getConnection()) {
            int personId = dbUtils.insert(conn,
                    "insert into person (ha_user_id, name, created) values (?, ?, ?)",
                    "ha-user", "Name", LocalDateTime.now()
            ).orElseThrow();

            assertThrows(SQLException.class, () -> dbUtils.insert(conn,
                    "insert into item_list (person_id, name, created) values (?, ?, ?)",
                    personId, "x".repeat(513), LocalDateTime.now()
            ));
        }
    }

    // The add-on restarts against the file it left, as on an update.
    @Test
    void data_isKeptWhenTheFileIsOpenedAgain() throws SQLException {
        try (Connection conn = db.getConnection()) {
            dbUtils.insert(conn,
                    "insert into person (ha_user_id, name, created) values (?, ?, ?)",
                    "ha-user", "Name", LocalDateTime.now()
            );
        }

        try (SqliteDb reopened = SqliteDb.openMigrated(db.getFile()); Connection conn = reopened.getConnection()) {
            List<String> names = dbUtils.select(conn,
                    "select name from person",
                    new Object[] {},
                    rs -> rs.getString("name").orElseThrow()
            );
            assertEquals(List.of("Name"), names);
        }
    }
}
