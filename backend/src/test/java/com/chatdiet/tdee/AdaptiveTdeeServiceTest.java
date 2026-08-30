package com.chatdiet.tdee;

import com.chatdiet.dashboard.DailyMacroCache;
import com.chatdiet.dashboard.DailyMacroCacheRepository;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.nutrition.DailyTarget;
import com.chatdiet.nutrition.DailyTargetRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdaptiveTdeeServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("adaptive-tdee-test.db"));
    }

    @Autowired
    private AdaptiveTdeeService adaptiveTdeeService;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @Autowired
    private DailyMacroCacheRepository dailyMacroCacheRepository;

    @Autowired
    private DailyTargetRepository dailyTargetRepository;

    @Autowired
    private DayBoundaryService dayBoundaryService;

    @BeforeEach
    void clearAll() {
        weightEntryRepository.deleteAll();
        dailyMacroCacheRepository.deleteAll();
        dailyTargetRepository.deleteAll();
    }

    @Test
    void computesTdeeFromIntakeAndAnOlsFitOfTheSmoothedWeightTrend() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(14);

        // Two weigh-ins only, 14 days apart, straight-line from 200 to 193 lbs - interpolateGaps
        // fills the days between linearly, so the EWMA smoothing runs over a clean, exact ramp,
        // and every one of the 15 days in [from, to] is a real (interpolated) trend point.
        weightEntryRepository.save(new WeightEntry(from.atTime(12, 0), 200.0));
        weightEntryRepository.save(new WeightEntry(to.atTime(12, 0), 193.0));

        // 7 days logged at a flat 2000 cal/day - exactly the MIN_LOGGED_DAYS threshold.
        for (int i = 0; i < 7; i++) {
            var date = to.minusDays(i);
            dailyMacroCacheRepository.save(new DailyMacroCache(null, date, 2000, 0, 0, 0, LocalDateTime.now()));
        }

        var result = adaptiveTdeeService.estimate();

        assertThat(result).isInstanceOf(TdeeResult.Estimate.class);
        var estimate = (TdeeResult.Estimate) result;
        assertThat(estimate.windowDays()).isEqualTo(14);
        assertThat(estimate.loggedDays()).isEqualTo(7);
        assertThat(estimate.caveat()).isNull();
        // Hand-computed: OLS slope over the 15-point EWMA-smoothed ramp (alpha=0.1, seeded at the
        // first value) from 200 to 193 is -0.2589045855271196 lb/day (not the raw -0.5/day input
        // ramp, and not the -3.5294/14 endpoint-difference the old estimator used) - fitting a
        // line through every point pulls the estimate toward the ramp's true average slope
        // instead of relying on just the two, still EWMA-lagged, endpoints.
        assertThat(estimate.weightChangeLbs()).isCloseTo(-3.6246641973796745, within(0.0001));
        // TDEE = avgIntake - slope*3500 = 2000 - (-0.2589045855271196*3500) = 2000 + 906.166... ≈ 2906
        assertThat(estimate.estimatedCalories()).isEqualTo(2906);
        // standard error of the slope (0.013395730353110246 lb/day) converted to calories/day.
        assertThat(estimate.standardErrorCalories()).isEqualTo(47);
    }

    @Test
    void flagsACaveatWhenTheCalorieGoalChangedRecently() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(14);
        weightEntryRepository.save(new WeightEntry(from.atTime(12, 0), 200.0));
        weightEntryRepository.save(new WeightEntry(to.atTime(12, 0), 193.0));
        for (int i = 0; i < 7; i++) {
            dailyMacroCacheRepository.save(
                    new DailyMacroCache(null, to.minusDays(i), 2000, 0, 0, 0, LocalDateTime.now()));
        }
        // A goal change 10 days before the window even starts - well within the 21-day lookback,
        // so the estimate could still be skewed by water/glycogen from that new phase.
        dailyTargetRepository.save(new DailyTarget(from.minusDays(10), 1800));

        var result = adaptiveTdeeService.estimate();

        assertThat(result).isInstanceOf(TdeeResult.Estimate.class);
        assertThat(((TdeeResult.Estimate) result).caveat()).contains("calorie target changed");
    }

    @Test
    void unavailableWhenFewerThanMinLoggedDays() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(14);
        weightEntryRepository.save(new WeightEntry(from.atTime(12, 0), 200.0));
        weightEntryRepository.save(new WeightEntry(to.atTime(12, 0), 193.0));

        // Only 3 logged days - below MIN_LOGGED_DAYS (7).
        for (int i = 0; i < 3; i++) {
            var date = to.minusDays(i);
            dailyMacroCacheRepository.save(new DailyMacroCache(null, date, 2000, 0, 0, 0, LocalDateTime.now()));
        }

        var result = adaptiveTdeeService.estimate();

        assertThat(result).isInstanceOf(TdeeResult.Unavailable.class);
        assertThat(((TdeeResult.Unavailable) result).reason()).contains("3 of the last 14 days");
    }

    @Test
    void unavailableWhenFewerThanMinTrendPointsExistInTheWindow() {
        var to = dayBoundaryService.today();
        // Weigh-ins only from the last 3 days - the trend map has no entries at all before the
        // first one, so only 3 of the 15 days in the window resolve to a real point.
        for (int i = 0; i < 3; i++) {
            weightEntryRepository.save(new WeightEntry(to.minusDays(i).atTime(12, 0), 190.0));
        }
        for (int i = 0; i < 7; i++) {
            dailyMacroCacheRepository.save(
                    new DailyMacroCache(null, to.minusDays(i), 2000, 0, 0, 0, LocalDateTime.now()));
        }

        var result = adaptiveTdeeService.estimate();

        assertThat(result).isInstanceOf(TdeeResult.Unavailable.class);
        assertThat(((TdeeResult.Unavailable) result).reason()).contains("days of weight-trend data");
    }
}
