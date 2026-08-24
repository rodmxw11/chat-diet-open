package com.chatdiet.backup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds an export archive containing a consistent SQLite hot-backup, produced via SQLite's own
 * {@code VACUUM INTO} command - copying the live database file directly could catch it mid-write.
 * Used for both the daily BackupJob and the on-demand /api/export endpoint.
 */
@Service
public class ExportService {

    private final JdbcTemplate jdbcTemplate;

    public ExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Builds a single zip archive containing a consistent SQLite database backup. Uses SQLite's
     * {@code VACUUM INTO} command to produce the database backup, so the resulting archive
     * reflects a single point-in-time snapshot rather than a copy of the live file taken
     * mid-write.
     *
     * @return the built archive as an in-memory zip
     * @throws UncheckedIOException if the backup file cannot be read or the zip cannot be assembled
     */
    public byte[] buildArchive() {
        try {
            var tempBackup = Files.createTempFile("chat-diet-backup", ".db");
            Files.delete(tempBackup); // VACUUM INTO refuses to write to an existing file
            try {
                jdbcTemplate.execute("VACUUM INTO '" + tempBackup.toAbsolutePath().toString().replace("'", "''") + "'");

                var buffer = new ByteArrayOutputStream();
                try (var zipOut = new ZipOutputStream(buffer)) {
                    zipOut.putNextEntry(new ZipEntry("chat-diet.db"));
                    Files.copy(tempBackup, zipOut);
                    zipOut.closeEntry();
                }
                return buffer.toByteArray();
            } finally {
                Files.deleteIfExists(tempBackup);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
