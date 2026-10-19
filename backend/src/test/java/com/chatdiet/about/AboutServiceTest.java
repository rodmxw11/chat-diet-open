package com.chatdiet.about;

import com.chatdiet.chat.ChatMessage;
import com.chatdiet.chat.ChatMessageRepository;
import com.chatdiet.chat.TokenUsage;
import com.chatdiet.day.DayBoundaryService;
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

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private DayBoundaryService dayBoundaryService;

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

    @Test
    void reportsConsistentJvmAndSystemMemory() {
        var memory = aboutService.current().memory();

        assertThat(memory.heapUsedBytes()).isPositive();
        assertThat(memory.heapUsedBytes()).isLessThanOrEqualTo(memory.heapCommittedBytes());
        assertThat(memory.heapCommittedBytes()).isLessThanOrEqualTo(memory.heapMaxBytes());
        assertThat(memory.heapFreeBytes()).isEqualTo(memory.heapMaxBytes() - memory.heapUsedBytes());
        assertThat(memory.nonHeapUsedBytes()).isPositive();
        // Temurin (what the app ships on) always provides the com.sun.management extension.
        assertThat(memory.systemTotalBytes()).isNotNull().isPositive();
        assertThat(memory.systemFreeBytes()).isNotNull().isBetween(0L, memory.systemTotalBytes());
    }

    @Test
    void sumsAiCostOverTheLastThirtyMetabolicDaysOnly() {
        var today = dayBoundaryService.today();
        var oldestCounted = today.minusDays(AboutService.AI_COST_DAYS - 1);
        // Haiku: 1M prompt at $1/M = $1 per turn. Opus: 100k completion at $25/M = $2.50.
        var haiku = new TokenUsage(1_000_000, 0, 1_000_000);
        var opus = new TokenUsage(0, 100_000, 100_000);
        // One at a time: SQLite's JDBC driver returns no generated keys for a batch insert.
        chatMessageRepository.save(new ChatMessage(today, "assistant", "a", today.atTime(12, 0), haiku, opus));
        chatMessageRepository.save(new ChatMessage(oldestCounted, "assistant", "b",
                oldestCounted.atTime(12, 0), haiku, null));
        chatMessageRepository.save(new ChatMessage(oldestCounted.minusDays(1), "assistant", "c",
                oldestCounted.minusDays(1).atTime(12, 0), haiku, opus));

        var aiCost = aboutService.current().aiCost();

        assertThat(aiCost.fromDate()).isEqualTo(oldestCounted);
        assertThat(aiCost.toDate()).isEqualTo(today);
        assertThat(aiCost.haikuCostUsd()).isEqualTo(2.0);
        assertThat(aiCost.opusCostUsd()).isEqualTo(2.5);
    }
}
