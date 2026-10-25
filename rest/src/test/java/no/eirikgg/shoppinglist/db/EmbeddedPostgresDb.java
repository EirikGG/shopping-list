package no.eirikgg.shoppinglist.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;

public final class EmbeddedPostgresDb implements Db, AutoCloseable {

    private final EmbeddedPostgres postgres;
    private final HikariDataSource dataSource;

    private EmbeddedPostgresDb() {
        try {
            this.postgres = EmbeddedPostgres.builder().start();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to start embedded PostgreSQL", e);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("shoppinglist-test-pool");
        config.setDataSource(postgres.getPostgresDatabase());
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(5_000);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (null != dataSource && !dataSource.isClosed()) {
            dataSource.close();
        }
        try {
            postgres.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to stop embedded PostgreSQL", e);
        }
    }

    public static EmbeddedPostgresDb createMigratedInstance() {
        return createInstanceMigratedWith("db/changelog/db.changelog-master.xml");
    }

    // Migrated with the given changelog only, e.g. to test a migration on data
    // in the shape it had before.
    public static EmbeddedPostgresDb createInstanceMigratedWith(String changelog) {
        EmbeddedPostgresDb db = new EmbeddedPostgresDb();

        try {
            LiquibaseMigrator migrator = new LiquibaseMigrator();
            migrator.migrate(db, changelog);
            return db;
        }
        catch (RuntimeException e) {
            try {
                db.close();
            }
            catch (RuntimeException closeException) {
                e.addSuppressed(closeException);
            }

            throw e;
        }
    }
}