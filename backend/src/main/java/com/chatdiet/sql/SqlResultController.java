package com.chatdiet.sql;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
public class SqlResultController {

    private final SqlResultStore sqlResultStore;

    public SqlResultController(SqlResultStore sqlResultStore) {
        this.sqlResultStore = sqlResultStore;
    }

    @GetMapping("/api/sql-results/{id}/csv")
    public ResponseEntity<String> csv(@PathVariable String id) {
        var result = sqlResultStore.get(id);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"query-result.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(toCsv(result.get()));
    }

    private String toCsv(QueryResult result) {
        var sb = new StringBuilder();
        sb.append(result.columns().stream().map(this::escapeCsv).collect(Collectors.joining(","))).append("\n");
        for (var row : result.rows()) {
            sb.append(row.stream()
                    .map(value -> escapeCsv(value == null ? "" : String.valueOf(value)))
                    .collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
