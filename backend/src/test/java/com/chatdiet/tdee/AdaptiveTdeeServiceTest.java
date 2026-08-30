package com.chatdiet.tdee;

import com.chatdiet.dashboard.DailyMacroCache;
import com.chatdiet.dashboard.DailyMacroCacheRepository;
import com.chatdiet.day.DayBoundaryService;
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
    private DayBoundaryService dayBoundaryService;

    @BeforeEach
    void clearAll() {
        weightEntryRepository.deleteAll();
        dailyMacroCacheRepository.deleteAll();
    }

    @Test
    void computesTdeeFromIntakeAndSmoothedWeightTrendChange() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(14);

        // Two weigh-ins only, 14 days apart, straight-line from 200 to 193 lbs - interpolateGaps
        // fills the days between linearly, so the EWMA smoothing runs over a clean, exact ramp.
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
        // Hand-computed via the documented EWMA (alpha=0.1, seeded at the first value) over the
        // 15-point linear ramp from 200 to 193: trend ends at ~196.4705, not the raw -7lb change -
        // the lag is the point of smoothing before using it in the TDEE formula.
        assertThat(estimate.weightChangeLbs()).isCloseTo(-3.5294556604732463, within(0.0001));
        // TDEE = avgIntake - (ΔweightLbs * 3500 / 14) = 2000 - (-3.5294... * 250) = 2000 + 882.36... ≈ 2882
        assertThat(estimate.estimatedCalories()).isEqualTo(2882);
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
    void unavailableWhenNoWeighInReachesFarEnoughBack() {
        var to = dayBoundaryService.today();
        // Weigh-ins only from the last 3 days - none anywhere near 14 days ago.
        for (int i = 0; i < 3; i++) {
            weightEntryRepository.save(new WeightEntry(to.minusDays(i).atTime(12, 0), 190.0));
        }
        for (int i = 0; i < 7; i++) {
            dailyMacroCacheRepository.save(
                    new DailyMacroCache(null, to.minusDays(i), 2000, 0, 0, 0, LocalDateTime.now()));
        }

        var result = adaptiveTdeeService.estimate();

        assertThat(result).isInstanceOf(TdeeResult.Unavailable.class);
        assertThat(((TdeeResult.Unavailable) result).reason()).contains("not enough weigh-in history");
    }
}
