package no.eirikgg.tgg.db;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;

public final class EmbeddedPostgresDb implements Db, AutoCloseable {

    private final EmbeddedPostgres postgres;
    private final DataSource dataSource;

    private EmbeddedPostgresDb() {
        try {
            this.postgres = EmbeddedPostgres.builder().start();
            this.dataSource = postgres.getPostgresDatabase();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to start embedded PostgreSQL", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        try {
            postgres.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to stop embedded PostgreSQL", e);
        }
    }

    public static EmbeddedPostgresDb createMigratedInstance() {
        EmbeddedPostgresDb db = new EmbeddedPostgresDb();

        try {
            LiquibaseMigrator migrator = new LiquibaseMigrator();
            migrator.migrate(db);
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