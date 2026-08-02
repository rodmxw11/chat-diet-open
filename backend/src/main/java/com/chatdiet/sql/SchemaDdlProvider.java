package com.chatdiet.sql;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Introspects the live, already-migrated H2 schema via H2's SCRIPT command rather than
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
     * Liquibase's changelog table), computed lazily on first call.
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
        var lines = jdbcTemplate.queryForList("SCRIPT NODATA NOPASSWORDS NOSETTINGS", String.class);
        return lines.stream()
                .filter(line -> line.startsWith("CREATE") && line.contains("TABLE"))
                .filter(line -> !line.contains("DATABASECHANGELOG"))
                .collect(Collectors.joining("\n"));
    }
}
