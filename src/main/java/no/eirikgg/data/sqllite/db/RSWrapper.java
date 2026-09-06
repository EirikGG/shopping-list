package no.eirikgg.data.sqllite.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public class RSWrapper {

    private ResultSet resultSet;

    public RSWrapper(ResultSet resultSet) {
        this.resultSet = resultSet;
    }




    public Optional<String> getString(String columnName) {
        try {
            String s = resultSet.getString(columnName);
            return Optional.ofNullable(s);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Integer> getInt(String columnName) {
        try {
            int i = resultSet.getInt(columnName);
            return Optional.ofNullable(i);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<LocalDate> getLocalDate(String columnName) {
        try {
            LocalDate date = resultSet.getObject(columnName, LocalDate.class);
            return Optional.ofNullable(date);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Optional<LocalDateTime> getLocalDateTime(String columnName) {
        try {
            LocalDateTime datetime = resultSet.getObject(columnName, LocalDateTime.class);
            return Optional.ofNullable(datetime);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
