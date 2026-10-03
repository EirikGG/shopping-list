package no.eirikgg.shoppinglist.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class PostgresDb implements Db {

    private static PostgresDb INSTANCE;
    private final HikariDataSource dataSource;

    private PostgresDb(
            String hostname,
            String port,
            String databaseName,
            String username,
            String password,
            String connectionPoolSize
    ) {
        int portNum = Integer.parseInt(port);
        int poolSize = Integer.parseInt(connectionPoolSize);
        Objects.requireNonNull(hostname);
        Objects.requireNonNull(databaseName);
        String jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(hostname, portNum, databaseName);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        HikariConfig config = new HikariConfig();
        config.setPoolName("shoppinglist-pool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTimeout(5_000);
        dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static PostgresDb getMigratedInstance(
        String hostname,
        String port,
        String databaseName,
        String username,
        String password,
        String connectionPoolSize
    ) {
        if  (null == INSTANCE) {
            INSTANCE = new PostgresDb(
                hostname,
                port,
                databaseName,
                username,
                password,
                connectionPoolSize
            );

            LiquibaseMigrator migrator = new LiquibaseMigrator();
            migrator.migrate(INSTANCE);
        }

        return INSTANCE;
    }

    @Override
    public void close() throws IOException {
        if (null == dataSource) {
            return;
        }
        if (dataSource.isClosed()) {
            return;
        }

        dataSource.close();
    }
}