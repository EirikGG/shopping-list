package no.eirikgg.data.sqllite.db;

import org.junit.jupiter.api.Test;


class LiquibaseMigratorTest {

    @Test
    void migrate() {
        EmbeddedPostgresDb.createMigratedInstance();
    }
}