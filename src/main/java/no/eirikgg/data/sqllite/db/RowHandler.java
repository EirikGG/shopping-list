package no.eirikgg.data.sqllite.db;


import java.sql.SQLException;

@FunctionalInterface
public interface RowHandler<T> {
    T handle(RSWrapper rs) throws SQLException;
}
