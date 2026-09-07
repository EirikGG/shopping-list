package no.eirikgg.tgg.db;

import org.junit.jupiter.api.Test;


class LiquibaseMigratorTest {

    @Test
    void migrate() {
        EmbeddedPostgresDb.createMigratedInstance();
    }
}