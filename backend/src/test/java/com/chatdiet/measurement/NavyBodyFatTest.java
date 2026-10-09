package com.chatdiet.measurement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NavyBodyFatTest {

    /** Hand-computed: 86.010·log10(36 − 15) − 70.041·log10(70) + 36.76 = 21.25 → 21.3. */
    @Test
    void menUseWaistMinusNeckAndHeight() {
        assertThat(NavyBodyFat.estimate("male", 70.0, 36.0, 15.0, null)).isEqualTo(21.3);
        assertThat(NavyBodyFat.estimate("M", 70.0, 36.0, 15.0, null)).isEqualTo(21.3);
    }

    /** Hand-computed: 163.205·log10(30 + 40 − 13) − 97.684·log10(65) − 78.387 = 31.09 → 31.1. */
    @Test
    void womenAlsoNeedTheHip() {
        assertThat(NavyBodyFat.estimate("female", 65.0, 30.0, 13.0, 40.0)).isEqualTo(31.1);
        assertThat(NavyBodyFat.estimate("female", 65.0, 30.0, 13.0, null)).isNull();
    }

    @Test
    void missingOrImpossibleInputsGiveNoEstimate() {
        assertThat(NavyBodyFat.estimate("", 70.0, 36.0, 15.0, null)).isNull();
        assertThat(NavyBodyFat.estimate("male", 0.0, 36.0, 15.0, null)).isNull();
        assertThat(NavyBodyFat.estimate("male", 70.0, null, 15.0, null)).isNull();
        assertThat(NavyBodyFat.estimate("male", 70.0, 36.0, null, null)).isNull();
        assertThat(NavyBodyFat.estimate("male", 70.0, 15.0, 16.0, null)).as("neck >= waist").isNull();
    }
}
