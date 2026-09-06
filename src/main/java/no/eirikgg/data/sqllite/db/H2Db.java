package no.eirikgg.data.sqllite.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

public class H2Db implements Db {

    private static final String USERNAME = "sa";
    private static final String PASSWORD = "";

    private final String jdbcUrl;

    public H2Db(String connectionUrl) {
        this.jdbcUrl = """
                %s;\
                MODE=PostgreSQL;\
                DATABASE_TO_LOWER=TRUE;\
                DEFAULT_NULL_ORDERING=HIGH;\
                AUTO_SERVER=TRUE
                """.formatted(connectionUrl);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                USERNAME,
                PASSWORD
        );
    }
}