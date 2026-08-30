package com.chatdiet.schema;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Introspects the live, already-migrated SQLite schema via {@code sqlite_master}/{@code PRAGMA
 * table_info}, the same source {@link com.chatdiet.sql.SchemaDdlProvider} uses for the SQL
 * agent's prompt - so this never drifts from what Liquibase actually applied either. Not cached:
 * this page is visited rarely, unlike the SQL agent's DDL block which is built on every request.
 */
@Service
public class SchemaInspectionService {

    private final JdbcTemplate jdbcTemplate;

    public SchemaInspectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TableSchema> tables() {
        var tableNames = jdbcTemplate.queryForList("""
                SELECT name FROM sqlite_master
                WHERE type = 'table'
                  AND name NOT LIKE 'sqlite_%'
                  AND UPPER(name) NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK')
                ORDER BY name
                """, String.class);

        return tableNames.stream()
                .map(tableName -> new TableSchema(tableName, columnsOf(tableName)))
                .toList();
    }

    /**
     * {@code PRAGMA table_info} doesn't accept a bind parameter, but {@code tableName} always
     * comes from {@code sqlite_master} above, never from user input, so string-building it in is
     * safe here.
     */
    private List<TableSchema.ColumnSchema> columnsOf(String tableName) {
        return jdbcTemplate.query("PRAGMA table_info(" + tableName + ")",
                (rs, rowNum) -> new TableSchema.ColumnSchema(rs.getString("name"), rs.getString("type")));
    }
}
