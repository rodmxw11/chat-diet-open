package com.chatdiet.measurement;

import com.chatdiet.intent.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MeasurementServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasourceAndProfile(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("measurement-test.db"));
        registry.add("chat-diet.profile.sex", () -> "male");
        registry.add("chat-diet.profile.height-in", () -> "70");
    }

    @Autowired
    private MeasurementService measurementService;

    @Autowired
    private LogMeasurementsTool logMeasurementsTool;

    @Autowired
    private BodyMeasurementRepository repository;

    @BeforeEach
    void clearAll() {
        repository.deleteAll();
    }

    @Test
    void loggingWaistAndNeckEchoesTheNavyEstimate() {
        var result = logMeasurementsTool.apply(new LogMeasurementsRequest(36.0, 15.0, null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        assertThat(((ToolResult.Success) result).message())
                .isEqualTo("Logged waist 36.0 in, neck 15.0 in. Navy body-fat estimate: 21.3%.");
    }

    /** Neck changes slowly, so a waist-only session reuses the last neck - and says so. */
    @Test
    void aWaistOnlySessionCarriesTheEarlierNeckForward() {
        var day = LocalDate.of(2026, 10, 1);
        repository.save(new BodyMeasurement(day.atTime(7, 0), 36.0, 15.0, null));
        repository.save(new BodyMeasurement(day.plusDays(7).atTime(7, 0), 35.0, null, null));

        var history = measurementService.history();

        assertThat(history).hasSize(2);
        var latest = history.getFirst();
        assertThat(latest.waistIn()).isEqualTo(35.0);
        assertThat(latest.neckIn()).isNull();
        assertThat(latest.neckCarried()).isTrue();
        assertThat(latest.bodyFatPct()).isEqualTo(NavyBodyFat.estimate("male", 70.0, 35.0, 15.0, null));
        assertThat(history.getLast().neckCarried()).isFalse();
    }

    @Test
    void anEmptyOrImplausibleRequestAsksInsteadOfSaving() {
        assertThat(logMeasurementsTool.apply(new LogMeasurementsRequest(null, null, null, null)))
                .isInstanceOf(ToolResult.NeedsClarification.class);
        assertThat(logMeasurementsTool.apply(new LogMeasurementsRequest(115.6, null, null, null)))
                .as("115.6 is within range")
                .isInstanceOf(ToolResult.Success.class);
        assertThat(logMeasurementsTool.apply(new LogMeasurementsRequest(150.0, null, null, null)))
                .as("150 inches is surely centimeters")
                .isInstanceOf(ToolResult.NeedsClarification.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void aBackdatedSessionIsFiledAtItsDate() {
        logMeasurementsTool.apply(new LogMeasurementsRequest(36.0, null, null, "2026-10-01"));

        assertThat(repository.findAll().getFirst().measuredAt()).isEqualTo(LocalDate.of(2026, 10, 1).atTime(12, 0));
    }
}
