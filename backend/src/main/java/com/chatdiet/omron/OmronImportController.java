package com.chatdiet.omron;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

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

    /**
     * Imports one OMRON export uploaded from the About page. A file that isn't an OMRON export, or
     * has a malformed row, is a 400 with a message for the user - and nothing is written.
     */
    @PostMapping(value = "/api/omron/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadReadings(@RequestParam("file") MultipartFile file) throws IOException {
        try {
            return ResponseEntity.ok(importService.importUpload(new String(file.getBytes(), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
