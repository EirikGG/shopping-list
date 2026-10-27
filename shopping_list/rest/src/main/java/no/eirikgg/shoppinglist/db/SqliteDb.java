package no.eirikgg.shoppinglist.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

// The database is one SQLite file. In Home Assistant it lives in the add-on's
// /data folder, which HA keeps across updates and includes in its backups.
public final class SqliteDb implements Db {

    // SQLite allows one writer at a time, so a few connections are plenty.
    private static final int POOL_SIZE = 4;
    // How long a connection waits for another's write before failing.
    private static final int BUSY_TIMEOUT_MS = 5_000;

    private final Path file;
    private final HikariDataSource dataSource;

    private SqliteDb(Path file) {
        this.file = file;

        // Set on every connection the pool opens. Foreign keys especially, as
        // SQLite does not check them unless asked, connection by connection.
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enforceForeignKeys(true);
        // Readers do not block the writer, nor the writer the readers.
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        // Safe with WAL: a power cut can lose the last commits, not corrupt.
        sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        sqliteConfig.setBusyTimeout(BUSY_TIMEOUT_MS);
        // Transactions take the write lock when they start. Otherwise two that
        // both read and then write can fail at once, without waiting.
        sqliteConfig.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        SQLiteDataSource sqliteDataSource = new SQLiteDataSource(sqliteConfig);
        sqliteDataSource.setUrl("jdbc:sqlite:" + file.toAbsolutePath());

        HikariConfig config = new HikariConfig();
        config.setPoolName("shoppinglist-pool");
        config.setDataSource(sqliteDataSource);
        config.setMaximumPoolSize(POOL_SIZE);
        config.setConnectionTimeout(BUSY_TIMEOUT_MS);
        dataSource = new HikariDataSource(config);
    }

    // Opens the database in the file, creating it and its folder if needed,
    // and brings the schema up to date.
    public static SqliteDb openMigrated(Path file) {
        try {
            Path folder = file.toAbsolutePath().getParent();
            if (null != folder) {
                Files.createDirectories(folder);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cant create the folder for " + file, e);
        }

        SqliteDb db = new SqliteDb(file);
        try {
            new LiquibaseMigrator().migrate(db);
            return db;
        }
        catch (RuntimeException e) {
            db.close();
            throw e;
        }
    }

    public Path getFile() {
        return file;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
