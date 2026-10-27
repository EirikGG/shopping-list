package no.eirikgg.shoppinglist.db;

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
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class DbUtils {
    private final Logger log = LoggerFactory.getLogger(getClass());

    // How timestamps are stored, as SQLite has no type for them. Fixed width,
    // so they sort and compare correctly as text. Read back by RSWrapper.
    public static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static void bind(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof Hidden hidden) {
                param = hidden.getValue();
            }
            if (param instanceof LocalDateTime timestamp) {
                param = TIMESTAMP_FORMAT.format(timestamp);
            }
            stmt.setObject(i + 1, param);
        }
    }

    public void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL cannot be null or blank");
        }
    }

    public void logSql(Duration duration, String sql, Object... params) {
        long millis = null == duration ? 0 : duration.toMillis();
        String timeTxt = "%d ms".formatted(millis);
        String formattedSql = formatSqlForLogging(sql, params);
        log.debug("[{}] - {}", timeTxt, formattedSql);
    }

    private String formatSqlForLogging(String sql, Object... params) {
        Object[] newParams = Arrays.stream(params)
            .map(e -> switch (e) {
                case Hidden ignored -> "<secret_parameter>";
                case Number n -> n;
                case null -> "NULL";
                default -> {
                    String oldParam = e.toString();
                    String escaped = oldParam.replaceAll("'", "''");
                    yield "'" + escaped + "'";
                }
            })
            .toArray(Object[]::new);


        String percentPlaceholder = "{percent_placeholder_" + System.currentTimeMillis() + "}";
        sql = sql.replace("%", percentPlaceholder);
        sql = sql.replace("?", "%s");
        sql = sql.trim();
        sql = sql.replace("\n\r", "\n\r\t");
        sql = sql.replace("\n", "\n\r\t");
        sql = sql.replaceAll("\\s+", " ");

        String formatted = sql.formatted(newParams);
        formatted = formatted.replace(percentPlaceholder, "%");
        return formatted;
    }



    public <R> List<R> select(
            Connection conn, @Language("SQL") String sql, Object param, RowHandler<R> rowMapper
    ) {
        return select(conn, sql, new Object[] {param}, rowMapper);
    }

    public <R> List<R> select(
            Connection conn, @Language("SQL") String sql, Object[] params, RowHandler<R> rowMapper
    ) {
        LocalDateTime startTime = LocalDateTime.now();
        validateSql(sql);

        try (
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            bind(stmt, params);

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
            logSql(duration, sql, params);
        }
    }

    public <R> Optional<R> selectSingle(
            Connection conn, @Language("SQL") String sql, Object param, RowHandler<R> rowMapper
    ) {
        return selectSingle(conn, sql, new Object[] {param}, rowMapper);
    }

    public <R> Optional<R> selectSingle(
            Connection conn, @Language("SQL") String sql, Object[] params, RowHandler<R> rowMapper
    ) {
        List<R> values = select(conn, sql, params, rowMapper);
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

    public Optional<Integer> insert(Connection conn, @Language("SQL") String sql, Object... params) throws SQLException {
        LocalDateTime startTime = LocalDateTime.now();
        validateSql(sql);

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(stmt, params);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows != 1) {
                throw new RuntimeException("Expected 1 affected row, got " + affectedRows);
            }

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                Object generatedKey = rs.next() ? rs.getObject(1) : null;
                if (generatedKey instanceof Number id) {
                    return Optional.of(id.intValue());
                }
                return Optional.empty();
            }
        }
        finally {
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            logSql(duration, sql, params);
        }
    }

    public Optional<Integer> update(Connection conn, @Language("SQL") String sql, Object... params) throws SQLException {
        LocalDateTime startTime = LocalDateTime.now();
        validateSql(sql);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            bind(stmt, params);

            int affectedRows = stmt.executeUpdate();
            return Optional.of(affectedRows);
        }
        finally {
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            logSql(duration, sql, params);
        }
    }
}
