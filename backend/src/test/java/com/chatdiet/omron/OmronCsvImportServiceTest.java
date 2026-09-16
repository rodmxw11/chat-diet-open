package com.chatdiet.omron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OmronCsvImportServiceTest {

    private static final String HEADER =
            "Date,Time,Systolic (mmHg),Diastolic (mmHg),Pulse (bpm),Symptoms,Consumed,TruRead,Notes\n";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("omron-import-test.db"));
    }

    @Autowired
    private OmronCsvImportService importService;

    @Autowired
    private OmronReadingRepository omronReadingRepository;

    @TempDir
    private Path csvDir;

    @BeforeEach
    void clearAll() {
        omronReadingRepository.deleteAll();
    }

    @Test
    void importsRowsFromCsv() throws IOException {
        var csv = writeCsv("report1.csv",
                "Sep 14 2026,05:22 am,150,79,61,-,-,-,-\n" +
                "Sep 12 2026,05:34 am,141,76,63,-,-,-,-\n");

        var rowsImported = importService.importFile(csv);

        assertThat(rowsImported).isEqualTo(2);
        assertThat(omronReadingRepository.count()).isEqualTo(2);
    }

    @Test
    void reimportingSameFileIsIdempotent() throws IOException {
        var csv = writeCsv("report1.csv", "Sep 14 2026,05:22 am,150,79,61,-,-,-,-\n");

        importService.importFile(csv);
        importService.importFile(csv);

        assertThat(omronReadingRepository.count()).isEqualTo(1);
    }

    @Test
    void overlappingDateRangeUpsertsRatherThanDuplicating() throws IOException {
        var first = writeCsv("report1.csv",
                "Sep 12 2026,05:34 am,141,76,63,-,-,-,-\n" +
                "Sep 14 2026,05:22 am,150,79,61,-,-,-,-\n");
        importService.importFile(first);

        // A later download overlaps the same date range and re-reports the Sep 14 reading, this
        // time with a corrected pulse value - simulating the real re-download workflow.
        var second = writeCsv("report2.csv",
                "Sep 14 2026,05:22 am,150,79,65,-,-,-,-\n" +
                "Sep 16 2026,06:00 am,130,80,70,-,-,-,-\n");
        importService.importFile(second);

        assertThat(omronReadingRepository.count()).isEqualTo(3);
        var updated = omronReadingRepository.findAll().stream()
                .filter(r -> r.timestamp().equals(LocalDateTime.of(2026, 9, 14, 5, 22)))
                .findFirst().orElseThrow();
        assertThat(updated.bpm()).isEqualTo(65);
    }

    @Test
    void importDirectoryImportsEveryCsvFileInIt() throws IOException {
        writeCsv("a.csv", "Sep 12 2026,05:34 am,141,76,63,-,-,-,-\n");
        writeCsv("b.csv", "Sep 14 2026,05:22 am,150,79,61,-,-,-,-\n");

        var result = importService.importDirectory(csvDir);

        assertThat(result.filesImported()).isEqualTo(2);
        assertThat(result.rowsImported()).isEqualTo(2);
        assertThat(omronReadingRepository.count()).isEqualTo(2);
    }

    private Path writeCsv(String fileName, String dataRows) throws IOException {
        var path = csvDir.resolve(fileName);
        Files.writeString(path, HEADER + dataRows);
        return path;
    }
}
