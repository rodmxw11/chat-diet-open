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
}
