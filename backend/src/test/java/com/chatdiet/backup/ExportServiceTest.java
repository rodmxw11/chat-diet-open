package com.chatdiet.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
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
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("export-test.db"));
    }

    @Autowired
    private ExportService exportService;

    @Test
    void archiveContainsDatabaseBackup() throws Exception {
        var archive = exportService.buildArchive();

        var entryNames = new ArrayList<String>();
        try (var zipIn = new ZipInputStream(new ByteArrayInputStream(archive))) {
            var entry = zipIn.getNextEntry();
            while (entry != null) {
                entryNames.add(entry.getName());
                entry = zipIn.getNextEntry();
            }
        }

        assertThat(entryNames).contains("chat-diet.db");
    }
}
