package no.eirikgg.tgg.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;

public class DbUtils {
    private final Logger log = LoggerFactory.getLogger(getClass());

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

    public static Object[] params(Object... params) {
        return params;
    }
}