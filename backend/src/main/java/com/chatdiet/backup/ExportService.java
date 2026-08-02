package com.chatdiet.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Builds one archive containing a consistent H2 hot-backup (via H2's own BACKUP TO command -
 * copying the live .mv.db file directly could catch it mid-write) plus every archived photo.
 * Used for both the daily BackupJob and the on-demand /api/export endpoint.
 */
@Service
public class ExportService {

    private final JdbcTemplate jdbcTemplate;
    private final Path photoArchiveDir;

    public ExportService(JdbcTemplate jdbcTemplate,
                          @Value("${chat-diet.photo-archive-dir:./data/photos}") String photoArchiveDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.photoArchiveDir = Path.of(photoArchiveDir);
    }

    /**
     * Builds a single zip archive containing a consistent H2 database backup plus every
     * archived photo. Uses H2's {@code BACKUP TO} command to produce the database backup, so
     * the resulting archive reflects a single point-in-time snapshot rather than a copy of the
     * live .mv.db file taken mid-write.
     *
     * @return the built archive as an in-memory zip
     * @throws UncheckedIOException if the H2 backup file or photo directory cannot be read, or
     *         the zip cannot be assembled
     */
    public byte[] buildArchive() {
        try {
            var tempBackup = Files.createTempFile("chat-diet-backup", ".zip");
            try {
                jdbcTemplate.execute("BACKUP TO '" + tempBackup.toAbsolutePath() + "'");

                var buffer = new ByteArrayOutputStream();
                try (var zipOut = new ZipOutputStream(buffer)) {
                    copyDbBackupEntries(tempBackup, zipOut);
                    copyPhotoEntries(zipOut);
                }
                return buffer.toByteArray();
            } finally {
                Files.deleteIfExists(tempBackup);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void copyDbBackupEntries(Path tempBackup, ZipOutputStream zipOut) throws IOException {
        try (var zipIn = new ZipInputStream(Files.newInputStream(tempBackup))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                zipOut.putNextEntry(new ZipEntry(entry.getName()));
                zipIn.transferTo(zipOut);
                zipOut.closeEntry();
            }
        }
    }

    private void copyPhotoEntries(ZipOutputStream zipOut) throws IOException {
        if (!Files.isDirectory(photoArchiveDir)) {
            return;
        }
        try (var files = Files.walk(photoArchiveDir)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                zipOut.putNextEntry(new ZipEntry("photos/" + photoArchiveDir.relativize(file)));
                Files.copy(file, zipOut);
                zipOut.closeEntry();
            }
        }
    }
}
