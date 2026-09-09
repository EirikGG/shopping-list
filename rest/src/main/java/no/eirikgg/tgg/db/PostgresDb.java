package no.eirikgg.tgg.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class PostgresDb implements Db {

    private static PostgresDb INSTANCE;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private PostgresDb(
            String hostname,
            String port,
            String databaseName,
            String username,
            String password
    ) {
        int portNum = Integer.parseInt(port);
        Objects.requireNonNull(hostname);
        Objects.requireNonNull(databaseName);
        this.jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(hostname, portNum, databaseName);
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            jdbcUrl,
            username,
            password
        );
    }

    public static PostgresDb getMigratedInstance(
            String hostname,
            String port,
            String databaseName,
            String username,
            String password
    ) {

        if  (null == INSTANCE) {
            INSTANCE = new PostgresDb(
                hostname,
                port,
                databaseName,
                username,
                password
            );

            LiquibaseMigrator migrator = new LiquibaseMigrator();
            migrator.migrate(INSTANCE);
        }

        return INSTANCE;
    }
}