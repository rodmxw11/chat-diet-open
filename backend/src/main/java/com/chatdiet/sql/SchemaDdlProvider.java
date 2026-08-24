package com.chatdiet.sql;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Introspects the live, already-migrated SQLite schema via {@code sqlite_master} rather than
 * hand-maintaining DDL text, so the SQL agent's prompt never drifts from what Liquibase actually
 * applied. Computed once and cached - the schema doesn't change again during the app's lifetime.
 */
@Component
public class SchemaDdlProvider {

    private final JdbcTemplate jdbcTemplate;
    private volatile String ddl;

    public SchemaDdlProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the cached {@code CREATE TABLE} DDL for all application tables (excluding
     * Liquibase's changelog table and SQLite's own bookkeeping tables), computed lazily on first
     * call.
     */
    public String ddl() {
        var local = ddl;
        if (local == null) {
            synchronized (this) {
                local = ddl;
                if (local == null) {
                    local = ddl = generate();
                }
            }
        }
        return local;
    }

    private String generate() {
        var lines = jdbcTemplate.queryForList("""
                SELECT sql FROM sqlite_master
                WHERE type = 'table'
                  AND name NOT LIKE 'sqlite_%'
                  AND UPPER(name) NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK')
                """, String.class);
        return lines.stream()
                .filter(line -> line != null)
                .collect(Collectors.joining("\n"));
    }
}
