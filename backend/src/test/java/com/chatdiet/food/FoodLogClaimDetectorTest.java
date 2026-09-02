package com.chatdiet.food;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static com.chatdiet.food.FoodLogClaimDetector.looksLikeFoodLogClaim;

class FoodLogClaimDetectorTest {

    @Test
    void flagsATypicalFoodLogConfirmation() {
        assertThat(looksLikeFoodLogClaim(
                "Logged: 2 IQ bars — **198 kcal, 18g protein, 8g carbs, 12g fat.**"))
                .isTrue();
    }

    @Test
    void flagsAQuotedStyleConfirmationToo() {
        assertThat(looksLikeFoodLogClaim(
                "Logged \"almond croissant\": 320 kcal, 9.0g protein, 28.0g carbs, 18.0g fat."))
                .isTrue();
    }

    @Test
    void doesNotFlagAnExerciseLogEvenThoughItMentionsCalories() {
        assertThat(looksLikeFoodLogClaim("Logged exercise: running, 30 min, 300 kcal burned."))
                .isFalse();
    }

    @Test
    void doesNotFlagAWeightLog() {
        assertThat(looksLikeFoodLogClaim("Logged weight: 182.4 lbs.")).isFalse();
    }

    @Test
    void doesNotFlagAVitalsLog() {
        assertThat(looksLikeFoodLogClaim("Logged BP 128/82, HR 68.")).isFalse();
    }

    @Test
    void doesNotFlagADigestiveEventLog() {
        assertThat(looksLikeFoodLogClaim("Logged: reflux (after lunch).")).isFalse();
    }

    @Test
    void doesNotFlagAListingWithNoMacros() {
        assertThat(looksLikeFoodLogClaim("""
                Yesterday you logged:
                - almond croissant at 8:42 AM: 320 kcal
                - Nature Valley granola at 2:11 PM: 343 kcal

                Total: 663 kcal""")).isFalse();
    }

    @Test
    void doesNotFlagAPlainAcknowledgement() {
        assertThat(looksLikeFoodLogClaim("Here you go.")).isFalse();
    }

    @Test
    void requiresAtLeastTwoMacroMentionsNotJustOne() {
        assertThat(looksLikeFoodLogClaim("That's about 150 kcal, mostly protein."))
                .as("only one macro word mentioned alongside calories - not distinctive enough on its own")
                .isFalse();
    }

    @Test
    void flagsATerseEstimateStyleConfirmationWithNoMacros() {
        assertThat(looksLikeFoodLogClaim("Logged: applesauce, 100 cal (estimate)."))
                .as("real observed miss - terse food-log echoes skip macros entirely")
                .isTrue();
    }

    @Test
    void flagsATerseConfirmationEvenWithoutTheEstimateTag() {
        assertThat(looksLikeFoodLogClaim("Logged: tuna in vegetable oil, 1 can, 260 cal."))
                .isTrue();
    }

    @Test
    void stillDoesNotFlagAnExerciseLogWithTheTerseHeuristic() {
        assertThat(looksLikeFoodLogClaim("Logged exercise: running, 30 min, 300 kcal burned."))
                .isFalse();
    }

    @Test
    void doesNotFlagADailyTargetReportThatHappensToSayLogged() {
        assertThat(looksLikeFoodLogClaim("You've logged 1200 kcal today, 800 remaining toward your target."))
                .isFalse();
    }

    @Test
    void doesNotFlagATdeeReport() {
        assertThat(looksLikeFoodLogClaim(
                "Your estimated TDEE over the last 14 days is about 2,400 cal/day."))
                .isFalse();
    }

    @Test
    void doesNotFlagACalorieGoalConfirmation() {
        assertThat(looksLikeFoodLogClaim("Logged your new calorie goal of 2000 kcal/day."))
                .isFalse();
    }

    @Test
    void flagsABatchEchoThatEndsInTheWordTotal() {
        assertThat(looksLikeFoodLogClaim("Logged: 2 hotdog buns, 100 cal total."))
                .as("real observed miss - the bare exclusion word \"total\" also matched this genuine "
                        + "batch echo, since it wasn't anchored to the listing tool's \"Total: \" report line")
                .isTrue();
    }
}
