package com.chatdiet.sql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only enforced at the connection (ACCESS_MODE_DATA=r) - this is the primary safety layer,
 * with SqlValidator as a cheap second one. A fresh connection is opened per query rather than
 * pooled; this is a single-user personal app, not a service under load.
 */
@Component
public class ReadOnlySqlExecutor {

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    /**
     * Bounds accidental cartesian-product-style queries without limiting the legitimate result
     * sizes the display row cap / CSV export are meant to support.
     */
    private static final int SAFETY_ROW_LIMIT = 20_000;

    private final String readOnlyJdbcUrl;

    public ReadOnlySqlExecutor(@Value("${spring.datasource.url}") String primaryJdbcUrl) {
        this.readOnlyJdbcUrl = primaryJdbcUrl + ";ACCESS_MODE_DATA=r";
    }

    public QueryResult execute(String sql, List<ParamDef> paramDefs, List<Object> params) {
        try (var connection = DriverManager.getConnection(readOnlyJdbcUrl);
             var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            bindParams(statement, paramDefs, params);

            try (var resultSet = statement.executeQuery()) {
                return readResultSet(resultSet);
            }
        } catch (SQLException e) {
            throw new SqlExecutionException("Query failed: " + e.getMessage(), e);
        }
    }

    private void bindParams(PreparedStatement statement, List<ParamDef> paramDefs, List<Object> params)
            throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            var type = i < paramDefs.size() ? paramDefs.get(i).type() : null;
            statement.setObject(i + 1, coerce(params.get(i), type));
        }
    }

    private Object coerce(Object value, String type) {
        if (value == null || type == null) {
            return value;
        }
        try {
            return switch (type.toUpperCase()) {
                case "DATE" -> java.sql.Date.valueOf(LocalDate.parse(value.toString()));
                case "DATETIME", "TIMESTAMP" -> {
                    var raw = value.toString();
                    var normalized = raw.length() == 10 ? raw + " 00:00:00" : raw.replace('T', ' ');
                    yield java.sql.Timestamp.valueOf(normalized);
                }
                default -> value;
            };
        } catch (Exception e) {
            return value;
        }
    }

    private QueryResult readResultSet(ResultSet resultSet) throws SQLException {
        var metaData = resultSet.getMetaData();
        var columnCount = metaData.getColumnCount();
        var columns = new ArrayList<String>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }

        var rows = new ArrayList<List<Object>>();
        while (resultSet.next() && rows.size() < SAFETY_ROW_LIMIT) {
            var row = new ArrayList<Object>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(normalize(resultSet.getObject(i)));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows);
    }

    private Object normalize(Object value) throws SQLException {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.sql.Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        return value;
    }
}
