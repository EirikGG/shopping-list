package no.eirikgg.data.sqllite.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

public class InMemoryH2Db implements Db {

    private static final String USERNAME = "sa";
    private static final String PASSWORD = "";

    private final String jdbcUrl;

    public InMemoryH2Db() {
        String databaseName = "test_" + UUID.randomUUID().toString().replace("-", "");

        this.jdbcUrl = """
                jdbc:h2:mem:%s;\
                MODE=PostgreSQL;\
                DATABASE_TO_LOWER=TRUE;\
                DEFAULT_NULL_ORDERING=HIGH;\
                DB_CLOSE_DELAY=-1
                """.formatted(databaseName);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                USERNAME,
                PASSWORD
        );
    }

    public static Db createMigratedInstance() {
        Db db = new InMemoryH2Db();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db);
        return db;
    }
}
