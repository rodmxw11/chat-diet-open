package com.chatdiet.dashboard;

import com.chatdiet.weight.WeightEntry;
import com.chatdiet.weight.WeightEntryRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeightTrendServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("weight-trend-test.db"));
    }

    @Autowired
    private WeightTrendService weightTrendService;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @BeforeEach
    void clearAll() {
        weightEntryRepository.deleteAll();
    }

    private void log(LocalDate date, double weightLbs) {
        weightEntryRepository.save(new WeightEntry(date.atTime(9, 0), weightLbs));
    }

    private Map<LocalDate, Double> trendByDate() {
        return weightTrendService.trend30Day().smoothed().stream()
                .collect(java.util.stream.Collectors.toMap(TrendPoint::date, TrendPoint::value));
    }

    @Test
    void smoothsConsecutiveDaysWithTrendWeightsRecurrenceRelation() {
        var today = LocalDate.now();
        log(today.minusDays(2), 200.0);
        log(today.minusDays(1), 202.0);
        log(today, 198.0);

        var trend = trendByDate();

        assertThat(trend.get(today.minusDays(2))).as("seed = first actual").isEqualTo(200.0, offset(0.001));
        assertThat(trend.get(today.minusDays(1))).isEqualTo(200.2, offset(0.001));
        assertThat(trend.get(today)).isEqualTo(199.98, offset(0.001));
    }

    @Test
    void interpolatesGapsInsteadOfJumpingOrFlatlining() {
        var today = LocalDate.now();
        var start = today.minusDays(5);
        log(start, 200.0);
        log(today, 210.0); // 5-day gap, 2 lbs/day interpolated

        var trend = trendByDate();

        // Hand-computed via the same recurrence: 200, 200.2, 200.58, 201.122, 201.8098, 202.62882
        assertThat(trend.get(today)).isEqualTo(202.62882, offset(0.001));
        assertThat(trend.get(start.plusDays(2)))
                .as("an interpolated day should get its own trend point, not just the endpoints")
                .isEqualTo(200.58, offset(0.001));

        var actualDates = weightTrendService.trend30Day().actual().stream().map(WeighIn::date).toList();
        assertThat(actualDates)
                .as("only real weigh-ins appear as actual points, not interpolated gap-fill days")
                .containsExactly(start, today);
    }

    @Test
    void warmsUpFromHistoryBeforeTheDisplayWindowInsteadOfResettingAtItsBoundary() {
        var today = LocalDate.now();
        // Long flat run well before the 30-day window converges the trend to exactly 150.
        for (int i = 60; i >= 32; i--) {
            log(today.minusDays(i), 150.0);
        }
        // First day inside the window jumps to 200, 3 days after the last pre-window reading -
        // the gap is interpolated (166.667, 183.333) and each of those days also gets smoothed:
        // 150 -> 151.667 -> 154.833 -> 159.35.
        var firstWindowDay = today.minusDays(29);
        log(firstWindowDay, 200.0);

        var trend = trendByDate();

        assertThat(trend.get(firstWindowDay))
                .as("should carry the converged, gap-smoothed pre-window trend forward, not reset to "
                        + "the raw 200 actual as if this were the first-ever reading")
                .isEqualTo(159.35, offset(0.01))
                .isNotEqualTo(200.0);
    }

    @Test
    void returnsEmptyResponseWithNoWeighIns() {
        var response = weightTrendService.trend30Day();

        assertThat(response.actual()).isEmpty();
        assertThat(response.smoothed()).isEmpty();
        assertThat(response.goal()).isNull();
    }
}
