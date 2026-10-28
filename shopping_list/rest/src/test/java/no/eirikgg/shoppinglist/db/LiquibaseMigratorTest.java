package no.eirikgg.shoppinglist.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


class LiquibaseMigratorTest {

    @Test
    void migrate() throws SQLException {
        try (TestDb db = TestDb.createMigratedInstance(); Connection conn = db.getConnection()) {
            List<String> tables = new DbUtils().select(conn, """
                    select name
                    from sqlite_master
                    where type = 'table'
                    and name not like 'sqlite_%'
                    and name not like 'DATABASECHANGELOG%'
                    order by name
                    """,
                    new Object[] {},
                    rs -> rs.getString("name").orElseThrow()
            );
            assertEquals(
                    List.of("attachment", "item", "item_label_mapping", "item_link", "item_list", "label", "person"),
                    tables
            );
        }
    }

    // The add-on migrates on every start, against the database it left.
    @Test
    void migrateAgain_changesNothing() {
        try (TestDb db = TestDb.createMigratedInstance()) {
            new LiquibaseMigrator().migrate(db);
        }
    }
}
