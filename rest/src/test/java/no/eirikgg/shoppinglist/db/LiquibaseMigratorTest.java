package no.eirikgg.shoppinglist.db;

import org.junit.jupiter.api.Test;


class LiquibaseMigratorTest {

    @Test
    void migrate() {
        EmbeddedPostgresDb.createMigratedInstance();
    }
}