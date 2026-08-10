package com.chatdiet.nutrition;

import com.chatdiet.exercise.ExerciseEntryRepository;
import com.chatdiet.food.FoodEntry;
import com.chatdiet.food.FoodEntryRepository;
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
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdaptiveTargetServiceTest {

    /** Weigh-ins this far apart, with {@link #DAILY_INTAKE} eaten each day in between. */
    private static final int WINDOW_DAYS = 7;
    private static final double START_WEIGHT = 200.0;
    private static final double END_WEIGHT = 198.0;
    private static final int DAILY_INTAKE = 1800;

    /**
     * The only TDEE consistent with the fixture: losing 2 lb over 7 days on 1800 kcal/day is a
     * 1000 kcal/day deficit, so maintenance must have been 2800. The adaptive loop is supposed to
     * converge here from any starting estimate.
     */
    private static final double IMPLIED_TRUE_TDEE = 2800.0;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void fixedProfileAndDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("adaptive-target-test"));
        // Pin the profile so the expected numbers don't depend on whoever's real application.yml.
        registry.add("chat-diet.profile.sex", () -> "MALE");
        registry.add("chat-diet.profile.birth-date", () -> "1970-01-01");
        registry.add("chat-diet.profile.height-in", () -> "69");
        registry.add("chat-diet.goal.weekly-rate-lbs", () -> "-1.0");
        registry.add("chat-diet.day-rollover-hour", () -> "4");
    }

    @Autowired
    private AdaptiveTargetService adaptiveTargetService;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private ExerciseEntryRepository exerciseEntryRepository;

    @Autowired
    private DailyTargetRepository dailyTargetRepository;

    private LocalDate latestWeighIn;

    @BeforeEach
    void resetAndSeed() {
        dailyTargetRepository.deleteAll();
        foodEntryRepository.deleteAll();
        weightEntryRepository.deleteAll();
        exerciseEntryRepository.deleteAll();

        latestWeighIn = LocalDate.now();
        LocalDate priorWeighIn = latestWeighIn.minusDays(WINDOW_DAYS);

        // 08:00 keeps every timestamp clear of the 4am metabolic-day rollover.
        weightEntryRepository.save(new WeightEntry(priorWeighIn.atTime(8, 0), START_WEIGHT));
        weightEntryRepository.save(new WeightEntry(latestWeighIn.atTime(8, 0), END_WEIGHT));

        for (int i = 0; i < WINDOW_DAYS; i++) {
            foodEntryRepository.save(new FoodEntry(
                    priorWeighIn.plusDays(i).atTime(12, 0), "test meal", DAILY_INTAKE,
                    null, null, null, "MANUAL"));
        }
    }

    /**
     * The bug this guards against: predicting from a fixed BMR left a constant error that was
     * re-applied in full on every recomputation, so the stored TDEE ramped a fixed amount per day
     * for as long as no new weight was logged - reaching physically impossible negatives (a real
     * session reported an effective TDEE of -3531) and dragging the calorie target with it.
     */
    @Test
    void effectiveTdeeConvergesInsteadOfRampingWhileNoNewWeightIsLogged() {
        var series = computeConsecutiveDays(30);

        assertThat(series.getLast())
                .as("30 recomputations from one pair of weigh-ins must settle on the TDEE the data implies")
                .isCloseTo(IMPLIED_TRUE_TDEE, org.assertj.core.data.Offset.offset(1.0));

        for (int i = 2; i < series.size(); i++) {
            double previousStep = Math.abs(series.get(i - 1) - series.get(i - 2));
            double currentStep = Math.abs(series.get(i) - series.get(i - 1));
            if (previousStep < 0.01) {
                break;
            }
            assertThat(currentStep)
                    .as("step %d must be smaller than the one before it - equal steps mean a ramp, not a feedback loop", i)
                    .isLessThan(previousStep);
        }
    }

    /**
     * Losing more weight than the baseline predicted means more was burned than assumed, so the
     * estimate has to rise. The original code subtracted where it should have added, driving it
     * the opposite way.
     */
    @Test
    void correctionMovesTowardTheObservedBurnRatherThanAwayFromIt() {
        var series = computeConsecutiveDays(3);

        assertThat(series.getFirst())
                .as("baseline BMR for a 198 lb 56-year-old is far below the 2800 the data implies, so the first correction must raise it")
                .isLessThan(IMPLIED_TRUE_TDEE);
        assertThat(series.get(1)).isGreaterThan(series.getFirst());
        assertThat(series.get(2)).isGreaterThan(series.get(1));
        assertThat(series.getLast()).isLessThanOrEqualTo(IMPLIED_TRUE_TDEE);
    }

    /** Two weigh-ins inside one day measure water weight, not fat, so they must not move the estimate. */
    @Test
    void weighInsTooCloseTogetherLeaveTheEstimateAlone() {
        weightEntryRepository.deleteAll();
        weightEntryRepository.save(new WeightEntry(latestWeighIn.atTime(7, 0), 200.0));
        weightEntryRepository.save(new WeightEntry(latestWeighIn.atTime(21, 0), 198.0));

        var first = requireTarget(latestWeighIn);
        var second = requireTarget(latestWeighIn.plusDays(1));

        assertThat(second.effectiveTdee())
                .as("a 2 lb intraday swing must not be treated as a metabolic signal")
                .isEqualTo(first.effectiveTdee());
    }

    /** A target already poisoned by the old bug must be pulled back into a plausible range, not inherited. */
    @Test
    void recoversFromAnAlreadyPoisonedBaseline() {
        dailyTargetRepository.save(new DailyTarget(latestWeighIn.minusDays(1), -4281, -3531.0, END_WEIGHT));

        var series = computeConsecutiveDays(20);

        assertThat(series)
                .as("no recomputation may report a negative or implausibly low TDEE, even starting from a corrupt row")
                .allSatisfy(tdee -> assertThat(tdee).isGreaterThanOrEqualTo(800.0));
        assertThat(series.getLast())
                .as("the loop should climb back to the TDEE the data implies")
                .isCloseTo(IMPLIED_TRUE_TDEE, org.assertj.core.data.Offset.offset(5.0));
        assertThat(requireTarget(latestWeighIn).targetCalories()).isPositive();
    }

    /** Computes a target for each of {@code days} consecutive metabolic days, returning the TDEE series. */
    private java.util.List<Double> computeConsecutiveDays(int days) {
        var series = new ArrayList<Double>();
        for (int i = 0; i < days; i++) {
            series.add(requireTarget(latestWeighIn.plusDays(i)).effectiveTdee());
        }
        return series;
    }

    private DailyTarget requireTarget(LocalDate date) {
        return adaptiveTargetService.getOrComputeTarget(date).orElseThrow();
    }
}
