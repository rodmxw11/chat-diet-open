package com.chatdiet.sql;

import java.util.regex.Pattern;

/**
 * Second layer of defense - the read-only connection (ACCESS_MODE_DATA=r) is what actually
 * makes writes impossible; this just rejects obviously-wrong SQL before it's even sent.
 */
final class SqlValidator {

    private static final Pattern DISALLOWED_KEYWORD = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|GRANT|REVOKE|EXEC|EXECUTE|CALL|SCRIPT)\\b",
            Pattern.CASE_INSENSITIVE);

    private SqlValidator() {
    }

    /**
     * Rejects SQL that is blank, contains multiple statements, doesn't start with SELECT/WITH, or
     * contains a disallowed write/DDL keyword.
     *
     * @throws IllegalArgumentException if the SQL fails any of these checks
     */
    static void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("No SQL was generated");
        }

        var trimmed = sql.strip();
        var withoutTrailingSemicolon = trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;

        if (withoutTrailingSemicolon.contains(";")) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }

        var startsWithSelect = withoutTrailingSemicolon.regionMatches(true, 0, "SELECT", 0, 6)
                || withoutTrailingSemicolon.regionMatches(true, 0, "WITH", 0, 4);
        if (!startsWithSelect) {
            throw new IllegalArgumentException("Only SELECT statements are allowed");
        }

        if (DISALLOWED_KEYWORD.matcher(withoutTrailingSemicolon).find()) {
            throw new IllegalArgumentException("Disallowed SQL keyword detected");
        }
    }
}
