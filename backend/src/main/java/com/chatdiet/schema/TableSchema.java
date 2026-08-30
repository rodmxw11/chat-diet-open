package com.chatdiet.schema;

import java.util.List;

/** One table's structure, for the read-only database-schema review page. */
public record TableSchema(String tableName, List<ColumnSchema> columns) {

    public record ColumnSchema(String name, String type) {
    }
}
