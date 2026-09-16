package com.chatdiet.omron;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * Triggers import of OMRON blood-pressure CSV exports dropped into the configured directory.
 * Re-running the import - e.g. after downloading a fresh report that overlaps the previous one's
 * date range - is safe: {@link OmronCsvImportService} upserts by timestamp, so already-imported
 * readings are simply overwritten with the same values rather than duplicated.
 */
@RestController
public class OmronImportController {

    private final OmronCsvImportService importService;
    private final Path importDir;

    public OmronImportController(OmronCsvImportService importService,
                                  @Value("${chat-diet.omron-import-dir:./data/OMRON-DOWNLOADS}") String importDir) {
        this.importService = importService;
        this.importDir = Path.of(importDir);
    }

    /** Imports every CSV report currently in the OMRON downloads directory. */
    @PostMapping("/api/omron/import")
    public OmronImportResult importReadings() {
        return importService.importDirectory(importDir);
    }
}
