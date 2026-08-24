package com.chatdiet.config;

import com.chatdiet.note.Note;
import com.chatdiet.note.NoteRepository;
import com.chatdiet.nutrition.DailyTarget;
import com.chatdiet.nutrition.DailyTargetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the date-conversion bug: reading DATE/DATETIME columns back through Spring
 * Data JDBC sometimes yields a Long (epoch millis) and sometimes a plain ISO String, depending on
 * query shape. The end-to-end tests round-trip a normally-written row (the shape Spring Data
 * JDBC's own writing path produces) against a real SQLite datasource; the direct converter tests
 * pin the Long/epoch-millis conversion path that isn't reachable through a TEXT-affinity column's
 * normal write path (SQLite coerces an inserted numeric value into its text digits on a
 * TEXT-affinity column, so it can't be reproduced by inserting through the DB) but is still a
 * shape the driver can hand back for other column configurations.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcCustomConversionsTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("jdbc-conversions-test.db"));
    }

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private DailyTargetRepository dailyTargetRepository;

    @Test
    void roundTripsLocalDateTimeWrittenAsAnIsoString() {
        var loggedAt = LocalDateTime.of(2026, 7, 4, 9, 30, 0);
        var saved = noteRepository.save(new Note(loggedAt, "string-path round trip"));

        var reloaded = noteRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.loggedAt()).isEqualTo(loggedAt);
    }

    @Test
    void roundTripsLocalDateWrittenAsAnIsoString() {
        var date = LocalDate.of(2026, 7, 4);
        var saved = dailyTargetRepository.save(new DailyTarget(date, 2000));

        var reloaded = dailyTargetRepository.findByTargetDate(date).orElseThrow();

        assertThat(reloaded.targetDate()).isEqualTo(date);
        assertThat(saved.id()).isNotNull();
    }

    @Test
    void longToLocalDateConverterInterpretsEpochMillisInTheSystemZone() {
        var date = LocalDate.of(2026, 7, 5);
        var epochMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        var converted = new JdbcDialectConfig.LongToLocalDateConverter().convert(epochMillis);

        assertThat(converted).isEqualTo(date);
    }

    @Test
    void longToLocalDateTimeConverterInterpretsEpochMillisInTheSystemZone() {
        var dateTime = LocalDateTime.of(2026, 7, 4, 9, 30, 0);
        var epochMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        var converted = new JdbcDialectConfig.LongToLocalDateTimeConverter().convert(epochMillis);

        assertThat(converted).isEqualTo(dateTime);
    }

    @Test
    void stringToLocalDateTimeConverterParsesAFullTimestamp() {
        var converted = new JdbcDialectConfig.StringToLocalDateTimeConverter().convert("2026-07-04T09:30:00");

        assertThat(converted).isEqualTo(LocalDateTime.of(2026, 7, 4, 9, 30, 0));
    }

    @Test
    void stringToLocalDateTimeConverterFallsBackToStartOfDayForABareDateString() {
        var converted = new JdbcDialectConfig.StringToLocalDateTimeConverter().convert("2026-07-04");

        assertThat(converted).isEqualTo(LocalDate.of(2026, 7, 4).atStartOfDay());
    }

    @Test
    void stringToLocalDateConverterParsesAnIsoDate() {
        var converted = new JdbcDialectConfig.StringToLocalDateConverter().convert("2026-07-04");

        assertThat(converted).isEqualTo(LocalDate.of(2026, 7, 4));
    }
}
