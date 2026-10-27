package no.eirikgg.shoppinglist.db;

import java.sql.ResultSet;
import java.sql.SQLException;
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

    // Stored as text in DbUtils.TIMESTAMP_FORMAT.
    public Optional<LocalDateTime> getLocalDateTime(String columnName) {
        return getString(columnName).map(text -> LocalDateTime.parse(text, DbUtils.TIMESTAMP_FORMAT));
    }
}
