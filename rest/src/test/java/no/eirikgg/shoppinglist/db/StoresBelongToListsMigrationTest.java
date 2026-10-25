package no.eirikgg.shoppinglist.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 0019 moves stores from people to lists. Runs it on data in the old shape.
class StoresBelongToListsMigrationTest {

    // From t0001 (Ola) and t0003 (Kari).
    private static final UUID OLA_SESSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KARI_SESSION = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final DbUtils dbUtils = new DbUtils();

    @Test
    void storesAreCopiedIntoTheirLists_andItemsFollow() throws SQLException {
        EmbeddedPostgresDb db = EmbeddedPostgresDb.createInstanceMigratedWith("db/changelog/test-before-0019.xml");
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-user-and-login.sql");
        migrator.migrate(db, "db/updates/t0003-create-second-test-user.sql");

        int ola, kari, sharedList, karisList, olaRemaItem, kariRemaItem, coopItem;
        try (Connection conn = db.getConnection()) {
            ola = personOf(conn, OLA_SESSION);
            kari = personOf(conn, KARI_SESSION);

            // Ola owns a list shared with Kari; Kari owns a list of her own.
            sharedList = list(conn, OLA_SESSION, ola, "Shared");
            member(conn, sharedList, ola, "OWNER", OLA_SESSION);
            member(conn, sharedList, kari, "MEMBER", KARI_SESSION);
            karisList = list(conn, KARI_SESSION, kari, "Karis");
            member(conn, karisList, kari, "OWNER", KARI_SESSION);

            // Old style: stores belong to people. Both have a "Rema".
            int olaRema = store(conn, OLA_SESSION, ola, "Rema");
            store(conn, OLA_SESSION, ola, "Kiwi");                // used nowhere
            int kariRema = store(conn, KARI_SESSION, kari, "Rema");
            int kariCoop = store(conn, KARI_SESSION, kari, "Coop");

            olaRemaItem = item(conn, OLA_SESSION, ola, sharedList, "Milk");
            kariRemaItem = item(conn, KARI_SESSION, kari, sharedList, "Bread");
            coopItem = item(conn, KARI_SESSION, kari, karisList, "Eggs");
            map(conn, OLA_SESSION, olaRema, olaRemaItem);
            map(conn, KARI_SESSION, kariRema, kariRemaItem);
            map(conn, KARI_SESSION, kariCoop, coopItem);
        }

        migrator.migrate(db);

        try (Connection conn = db.getConnection()) {
            // The shared list gets its owner's stores, plus every store used on
            // it; the two "Rema" become one. Kari's Coop is not used there.
            assertEquals(List.of("Kiwi", "Rema"), storeNames(conn, sharedList));
            // Kari's own list gets her stores.
            assertEquals(List.of("Coop", "Rema"), storeNames(conn, karisList));

            // Items point to their list's copy.
            int sharedRema = storeId(conn, sharedList, "Rema");
            assertEquals(List.of(sharedRema), storesOfItem(conn, olaRemaItem));
            assertEquals(List.of(sharedRema), storesOfItem(conn, kariRemaItem));
            assertEquals(List.of(storeId(conn, karisList, "Coop")), storesOfItem(conn, coopItem));

            // The old person-level stores are kept, but deleted.
            assertTrue(dbUtils.select(conn,
                    "select store_id from store where item_list_id is null and deleted is null",
                    new Object[] {},
                    rs -> rs.getInt("store_id").orElseThrow()
            ).isEmpty());
        }
        db.close();
    }

    private int personOf(Connection conn, UUID session) {
        return dbUtils.selectSingle(conn, """
                select login.person_id
                from session as s
                join person_login as login on login.person_login_id = s.person_login_id
                where s.session_id = ?
                """, session, rs -> rs.getInt("person_id").orElseThrow()).orElseThrow();
    }

    private int list(Connection conn, UUID session, int person, String name) throws SQLException {
        return dbUtils.insert(conn,
                "insert into item_list (session_id, person_id, name, created) values (?, ?, ?, ?)",
                session, person, name, LocalDateTime.now()).orElseThrow();
    }

    private void member(Connection conn, int list, int person, String role, UUID session) throws SQLException {
        dbUtils.update(conn,
                "insert into item_list_person (item_list_id, person_id, role, session_id, created) values (?, ?, ?, ?, ?)",
                list, person, role, session, LocalDateTime.now());
    }

    private int store(Connection conn, UUID session, int person, String name) throws SQLException {
        return dbUtils.insert(conn,
                "insert into store (session_id, person_id, name, created) values (?, ?, ?, ?)",
                session, person, name, LocalDateTime.now()).orElseThrow();
    }

    private int item(Connection conn, UUID session, int person, int list, String name) throws SQLException {
        return dbUtils.insert(conn,
                "insert into item (item_list_id, session_id, person_id, name, created) values (?, ?, ?, ?, ?)",
                list, session, person, name, LocalDateTime.now()).orElseThrow();
    }

    private void map(Connection conn, UUID session, int store, int item) throws SQLException {
        dbUtils.update(conn,
                "insert into item_store_mapping (session_id, store_id, item_id) values (?, ?, ?)",
                session, store, item);
    }

    private List<String> storeNames(Connection conn, int list) {
        return dbUtils.select(conn,
                "select name from store where item_list_id = ? and deleted is null order by name",
                list, rs -> rs.getString("name").orElseThrow());
    }

    private int storeId(Connection conn, int list, String name) {
        return dbUtils.selectSingle(conn,
                "select store_id from store where item_list_id = ? and name = ? and deleted is null",
                new Object[] {list, name}, rs -> rs.getInt("store_id").orElseThrow()).orElseThrow();
    }

    private List<Integer> storesOfItem(Connection conn, int item) {
        return dbUtils.select(conn,
                "select store_id from item_store_mapping where item_id = ? order by store_id",
                item, rs -> rs.getInt("store_id").orElseThrow());
    }
}
