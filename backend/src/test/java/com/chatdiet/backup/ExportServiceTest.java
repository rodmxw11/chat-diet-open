package com.chatdiet.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExportServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasourceAndPhotoDir(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("export-test"));
        registry.add("chat-diet.photo-archive-dir", () -> tempDir.resolve("photos").toString());
    }

    @Autowired
    private ExportService exportService;

    @Test
    void archiveContainsDatabaseBackupAndPhotos() throws Exception {
        var photoDir = tempDir.resolve("photos");
        Files.createDirectories(photoDir);
        Files.write(photoDir.resolve("sample.jpg"), new byte[]{1, 2, 3});

        var archive = exportService.buildArchive();

        var entryNames = new ArrayList<String>();
        try (var zipIn = new ZipInputStream(new ByteArrayInputStream(archive))) {
            var entry = zipIn.getNextEntry();
            while (entry != null) {
                entryNames.add(entry.getName());
                entry = zipIn.getNextEntry();
            }
        }

        assertThat(entryNames).anyMatch(name -> name.endsWith(".mv.db"));
        assertThat(entryNames).contains("photos/sample.jpg");
    }
}
