package com.chatdiet.about;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AboutServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("about-test.db"));
    }

    @Autowired
    private AboutService aboutService;

    @Test
    void reportsStartupTimeAndNonNegativeUptime() {
        var info = aboutService.current();

        assertThat(info.startupTime()).isBeforeOrEqualTo(Instant.now());
        assertThat(info.uptimeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(info.dayRolloverHour()).isEqualTo(4);
        assertThat(info.javaRuntime()).isNotBlank();
        assertThat(info.os()).isNotBlank();
    }

    @Test
    void reportsDatabaseSizeForTheLiveSqliteFile() {
        var info = aboutService.current();

        // The temp SQLite file exists once Liquibase has run against it (which SpringBootTest
        // context startup already triggered), so this should resolve rather than come back null.
        assertThat(info.databaseSizeBytes()).isNotNull().isGreaterThan(0);
    }
}
