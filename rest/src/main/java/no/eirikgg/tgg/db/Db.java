package no.eirikgg.tgg.db;

import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public interface Db {

    Logger log = LoggerFactory.getLogger(Db.class);
    DbUtils dbUtils = new DbUtils();



    Connection getConnection() throws SQLException;



    default <R> List<R> select(
            Connection conn, @Language("SQL") String sql, Object param, RowHandler<R> rowMapper
    ) {
        return select(conn, sql, new Object[] {param}, rowMapper);
    }

    default <R> List<R> select(
            Connection conn, @Language("SQL") String sql, Object[] params, RowHandler<R> rowMapper
    ) {
        LocalDateTime startTime = LocalDateTime.now();
        dbUtils.validateSql(sql);

        try (
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof Hidden hidden) {
                    param = hidden.getValue();
                }
                stmt.setObject(i + 1, param);
            }

            List<R> rows = new LinkedList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                RSWrapper wrapped = new RSWrapper(rs);
                while (rs.next()) {
                    R mapped = rowMapper.handle(wrapped);
                    rows.add(mapped);
                }
            }

            return rows;
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            dbUtils.logSql(duration, sql, params);
        }
    }

    default <R> Optional<R> selectSingle(
            Connection conn, @Language("SQL") String sql, Object param, RowHandler<R> rowMapper
    ) {
        List<R> values = select(conn, sql, new Object[]{param}, rowMapper);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        else if  (values.size() == 1) {
            R value = values.get(0);
            return Optional.of(value);
        }
        else {
            throw new IllegalStateException("Multiple values found in select one method");
        }
    }

    default Optional<Integer> insert(
            Connection conn, @Language("SQL") String sql, Object... params
    ) {
        LocalDateTime startTime = LocalDateTime.now();
        dbUtils.validateSql(sql);

        try (
            PreparedStatement stmt = conn.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            )
        ) {
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof Hidden hidden) {
                    param = hidden.getValue();
                }
                stmt.setObject(i + 1, param);
            }

            int affectedRows = stmt.executeUpdate();
            if (affectedRows != 1) {
                throw new RuntimeException("Expected 1 affected row, got " + affectedRows);
            }

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                Object generatedKey = rs.next() ? rs.getObject(1) : null;
                if (generatedKey instanceof Integer id) {
                    return Optional.ofNullable(id);
                }
                return Optional.empty();
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            dbUtils.logSql(duration, sql, params);
        }
    }

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
