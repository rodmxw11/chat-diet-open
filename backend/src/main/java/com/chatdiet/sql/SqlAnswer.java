package com.chatdiet.sql;

import java.util.List;

public record SqlAnswer(
        String sqlText,
        List<String> columns,
        List<List<Object>> rows,
        boolean truncated,
        int totalRows,
        String csvId
) {
}
