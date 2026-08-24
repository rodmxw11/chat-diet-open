package com.chatdiet.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackupJobTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasourceAndBackupDir(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("backup-job-test.db"));
        registry.add("chat-diet.backup-dir", () -> tempDir.resolve("backups").toString());
    }

    @Autowired
    private BackupJob backupJob;

    @Test
    void writesAnArchiveAndPrunesBeyondRetention() throws Exception {
        var backupDir = tempDir.resolve("backups");
        Files.createDirectories(backupDir);
        for (var i = 0; i < 20; i++) {
            var stale = backupDir.resolve("old-backup-" + i + ".zip");
            Files.write(stale, new byte[]{0});
            Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.from(
                    Instant.now().minus(i + 1, ChronoUnit.DAYS)));
        }

        var written = backupJob.run();

        assertThat(Files.exists(written)).isTrue();

        try (var files = Files.list(backupDir)) {
            assertThat(files.count())
                    .as("retention should cap the number of backup files kept")
                    .isLessThanOrEqualTo(14);
        }
    }
}
