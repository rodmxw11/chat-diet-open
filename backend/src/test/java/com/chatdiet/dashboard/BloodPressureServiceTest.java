package com.chatdiet.dashboard;

import com.chatdiet.omron.OmronReading;
import com.chatdiet.omron.OmronReadingRepository;
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
class BloodPressureServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("blood-pressure-test.db"));
    }

    @Autowired
    private BloodPressureService bloodPressureService;

    @Autowired
    private OmronReadingRepository omronReadingRepository;

    @BeforeEach
    void clearAll() {
        omronReadingRepository.deleteAll();
    }

    private void log(LocalDate date, int systolic, int diastolic, int bpm) {
        omronReadingRepository.save(new OmronReading(null, date.atTime(8, 0), systolic, diastolic, bpm));
    }

    @Test
    void returnsReadingsInRangeOldestFirst() {
        var today = LocalDate.now();
        log(today.minusDays(2), 130, 80, 65);
        log(today.minusDays(1), 128, 78, 62);
        log(today, 125, 75, 60);

        var readings = bloodPressureService.readings(7);

        assertThat(readings).hasSize(3);
        assertThat(readings.get(0).systolic()).isEqualTo(130);
        assertThat(readings.get(2).systolic()).isEqualTo(125);
    }

    @Test
    void excludesReadingsOutsideTheRequestedWindow() {
        var today = LocalDate.now();
        log(today.minusDays(10), 140, 90, 70);
        log(today, 120, 76, 58);

        var readings = bloodPressureService.readings(7);

        assertThat(readings).hasSize(1);
        assertThat(readings.getFirst().systolic()).isEqualTo(120);
    }
}
