package com.chatdiet.weight;

import org.junit.jupiter.api.Test;

import static com.chatdiet.weight.WeightLogClaimDetector.looksLikeWeightLogClaim;
import static org.assertj.core.api.Assertions.assertThat;

class WeightLogClaimDetectorTest {

    @Test
    void flagsTheToolsExactEchoText() {
        assertThat(looksLikeWeightLogClaim("Logged weight: 180.5 lbs.")).isTrue();
    }

    @Test
    void flagsAParaphraseThatDropsTheWordWeight() {
        assertThat(looksLikeWeightLogClaim("Logged 178.2 lbs.")).isTrue();
    }

    @Test
    void flagsAColonStyleParaphrase() {
        assertThat(looksLikeWeightLogClaim("Logged: 179.4 lbs.")).isTrue();
    }

    @Test
    void flagsKilogramsToo() {
        assertThat(looksLikeWeightLogClaim("Logged weight: 82.3 kg.")).isTrue();
    }

    @Test
    void doesNotFlagACorrection() {
        assertThat(looksLikeWeightLogClaim("Corrected weight: 182.4 lbs."))
                .as("a correction overwrites an existing row rather than needing a fresh one verified")
                .isFalse();
    }

    @Test
    void doesNotFlagAWeightProjectionReport() {
        assertThat(looksLikeWeightLogClaim("Projected weight on 2026-12-01: 170.0 lbs at the current rate."))
                .isFalse();
    }

    @Test
    void doesNotFlagATdeeReportThatMentionsPounds() {
        assertThat(looksLikeWeightLogClaim(
                "Your estimated TDEE is about 2,400 cal/day (based on 10 days of food logs and a "
                        + "-1.2 lb fitted weight-trend change)."))
                .isFalse();
    }

    @Test
    void doesNotFlagAFoodLogWithNoWeightUnit() {
        assertThat(looksLikeWeightLogClaim("Logged: applesauce, 100 cal (estimate).")).isFalse();
    }

    @Test
    void doesNotFlagAPlainAcknowledgement() {
        assertThat(looksLikeWeightLogClaim("Got it.")).isFalse();
    }
}
