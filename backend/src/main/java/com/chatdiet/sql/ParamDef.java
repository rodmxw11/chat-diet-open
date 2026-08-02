package com.chatdiet.sql;

/**
 * Describes one {@code ?} placeholder in a composed SQL statement.
 *
 * @param type the placeholder's declared type as one of {@code DATE}, {@code DATETIME},
 *             {@code STRING}, or {@code NUMBER} (as produced by the SQL composer), used to
 *             coerce the bound value before it's sent to JDBC
 */
public record ParamDef(String name, String type) {
}
