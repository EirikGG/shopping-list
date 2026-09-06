package no.eirikgg.data.sqllite.db;

import org.junit.jupiter.api.Test;


class LiquibaseMigratorTest {

    @Test
    void migrate() {
        InMemoryH2Db h2Db2 = new InMemoryH2Db();
        h2Db2.createMigratedInstance();
    }
}