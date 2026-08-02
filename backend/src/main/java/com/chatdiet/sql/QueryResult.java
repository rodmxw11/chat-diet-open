package com.chatdiet.sql;

import java.util.List;

/**
 * Raw, uncapped result of executing a SQL statement via {@link ReadOnlySqlExecutor}.
 *
 * @param columns column labels, in select order
 * @param rows    result rows, each the same length as {@code columns}; values are normalized
 *                JDBC types (e.g. {@link java.time.LocalDate}/{@link java.time.LocalDateTime}
 *                rather than {@code java.sql} types)
 */
public record QueryResult(List<String> columns, List<List<Object>> rows) {
}
