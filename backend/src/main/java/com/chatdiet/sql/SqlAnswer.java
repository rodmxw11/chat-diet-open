package com.chatdiet.sql;

import java.util.List;

/**
 * Final answer returned by {@link SqlAgentService#answer}, ready to render in the chat response:
 * the SQL that was run plus a display-capped view of its results.
 *
 * @param rows      up to {@code DISPLAY_ROW_CAP} rows for inline display (the full result set is
 *                  available separately for CSV download via {@code csvId})
 * @param truncated whether {@code rows} was capped and doesn't contain every matching row
 * @param totalRows the true number of rows the query matched, regardless of truncation
 * @param csvId     key into {@link SqlResultStore} for downloading the full, uncapped result as
 *                  CSV
 */
public record SqlAnswer(
        String sqlText,
        List<String> columns,
        List<List<Object>> rows,
        boolean truncated,
        int totalRows,
        String csvId
) {
}
