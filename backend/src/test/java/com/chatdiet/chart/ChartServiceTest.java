package com.chatdiet.chart;

import com.chatdiet.food.FoodEntry;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.weight.WeightEntry;
import com.chatdiet.weight.WeightEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChartServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("chart-test"));
    }

    @Autowired
    private ChartService chartService;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
        weightEntryRepository.deleteAll();
    }

    @Test
    void sumsCaloriesPerDayBucket() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 20.0, 40.0, 10.0, "LOG_FOOD"));
        foodEntryRepository.save(new FoodEntry(today.atTime(13, 0), "lunch", 600, 30.0, 60.0, 20.0, "LOG_FOOD"));

        var request = new ChartRequest(ChartMetric.CALORIES, today, today, Granularity.DAY, false, false);
        var series = chartService.compute(request);

        assertThat(series).hasSize(1);
        assertThat(series.get(0).points()).hasSize(1);
        assertThat(series.get(0).points().get(0).value()).isEqualTo(1000.0);
    }

    @Test
    void cumulativeHourlyBucketsAccumulateAcrossTheDay() {
        var now = LocalDateTime.now();
        var earlierToday = now.getHour() >= 2 ? now.minusHours(2) : now;
        foodEntryRepository.save(new FoodEntry(earlierToday, "snack", 200, 5.0, 20.0, 5.0, "LOG_FOOD"));
        foodEntryRepository.save(new FoodEntry(now, "snack2", 300, 5.0, 20.0, 5.0, "LOG_FOOD"));

        var today = LocalDate.now();
        var request = new ChartRequest(ChartMetric.CALORIES, today, today, Granularity.HOUR, false, false);
        var series = chartService.compute(request);

        assertThat(series).hasSize(1);
        var lastValue = series.get(0).points().get(series.get(0).points().size() - 1).value();
        assertThat(lastValue)
                .as("hourly buckets are always cumulative-against-target regardless of the cumulative flag")
                .isEqualTo(500.0);
    }

    @Test
    void weightMetricAveragesReadingsWithoutForcingCumulative() {
        var today = LocalDate.now();
        weightEntryRepository.save(new WeightEntry(today.atTime(7, 0), 180.0));
        weightEntryRepository.save(new WeightEntry(today.atTime(20, 0), 182.0));

        var request = new ChartRequest(ChartMetric.WEIGHT, today, today, Granularity.DAY, false, false);
        var series = chartService.compute(request);

        assertThat(series).hasSize(1);
        assertThat(series.get(0).points().get(0).value()).isEqualTo(181.0);
    }

    @Test
    void macrosMetricReturnsThreeSeries() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 20.0, 40.0, 10.0, "LOG_FOOD"));

        var request = new ChartRequest(ChartMetric.MACROS, today, today, Granularity.DAY, false, false);
        var series = chartService.compute(request);

        assertThat(series).extracting(ChartSeries::label)
                .containsExactly("Protein (g)", "Carbs (g)", "Fat (g)");
    }
}
