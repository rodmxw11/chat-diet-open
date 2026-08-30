package com.chatdiet.schema;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST surface for the read-only database-schema review page. */
@RestController
public class SchemaController {

    private final SchemaInspectionService schemaInspectionService;

    public SchemaController(SchemaInspectionService schemaInspectionService) {
        this.schemaInspectionService = schemaInspectionService;
    }

    @GetMapping("/api/schema")
    public List<TableSchema> tables() {
        return schemaInspectionService.tables();
    }
}
