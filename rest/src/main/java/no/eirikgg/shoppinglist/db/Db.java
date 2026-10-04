package no.eirikgg.shoppinglist.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;

public interface Db extends Closeable {

    Logger log = LoggerFactory.getLogger(Db.class);



    Connection getConnection() throws SQLException;



    default boolean dbConnectionOk() {
        try (Connection connection = getConnection()) {
            return connection.isValid(2);
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }
}
