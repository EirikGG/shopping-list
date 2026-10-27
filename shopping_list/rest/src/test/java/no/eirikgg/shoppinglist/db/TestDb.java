package no.eirikgg.shoppinglist.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.stream.Stream;

// A fresh database for a test, set up exactly as in production: a SQLite file,
// opened and migrated by SqliteDb. Closing it deletes the file.
public final class TestDb implements Db, AutoCloseable {

    private final Path folder;
    private final SqliteDb db;

    private TestDb(Path folder, SqliteDb db) {
        this.folder = folder;
        this.db = db;
    }

    public static TestDb createMigratedInstance() {
        try {
            Path folder = Files.createTempDirectory("shoppinglist-test");
            return new TestDb(folder, SqliteDb.openMigrated(folder.resolve("shoppinglist.db")));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to create a test database", e);
        }
    }

    public Path getFile() {
        return db.getFile();
    }

    // For uploaded files, deleted with the database.
    public Path getFilesFolder() {
        return folder.resolve("files");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return db.getConnection();
    }

    // Also removes SQLite's -wal and -shm files. Safe to call more than once:
    // the app closes its database itself when the test server stops.
    @Override
    public void close() {
        db.close();
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to delete the test database", e);
        }
    }
}
