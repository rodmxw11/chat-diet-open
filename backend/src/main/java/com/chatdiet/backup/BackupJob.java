package com.chatdiet.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.Comparator;

/** Daily automatic backup of the H2 database plus photos, with simple retention. */
@Component
public class BackupJob {

    private static final int RETENTION_COUNT = 14;

    private final ExportService exportService;
    private final Path backupDir;

    public BackupJob(ExportService exportService, @Value("${chat-diet.backup-dir:./data/backups}") String backupDir) {
        this.exportService = exportService;
        this.backupDir = Path.of(backupDir);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runScheduled() {
        run();
    }

    /**
     * Builds and writes a dated backup archive to the backup directory, then prunes old backups
     * beyond the retention count.
     *
     * @return the path of the newly written backup archive
     * @throws UncheckedIOException if writing the archive or pruning old backups fails
     */
    public Path run() {
        try {
            Files.createDirectories(backupDir);
            var path = backupDir.resolve("chat-diet-backup-" + LocalDate.now() + ".zip");
            Files.write(path, exportService.buildArchive());
            pruneOldBackups();
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void pruneOldBackups() throws IOException {
        try (var files = Files.list(backupDir)) {
            var sorted = files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(this::lastModifiedSafely).reversed())
                    .toList();
            for (var i = RETENTION_COUNT; i < sorted.size(); i++) {
                Files.deleteIfExists(sorted.get(i));
            }
        }
    }

    private FileTime lastModifiedSafely(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
