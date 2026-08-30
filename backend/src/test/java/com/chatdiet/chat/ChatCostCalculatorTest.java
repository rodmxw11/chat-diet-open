package com.chatdiet.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCostCalculatorTest {

    @Test
    void computesCostFromTokenCountsAndPerMillionRates() {
        // 1,000,000 prompt tokens at $1/million + 200,000 completion tokens at $5/million = $1 + $1 = $2
        assertThat(ChatCostCalculator.roundToCents(1_000_000, 200_000, 1.0, 5.0)).isEqualTo(2.0);
    }

    @Test
    void roundsToTheNearestCent() {
        // 1 token at $1/million = $0.000001 - should round down to $0.00, not linger as a fraction of a cent.
        assertThat(ChatCostCalculator.roundToCents(1, 0, 1.0, 5.0)).isEqualTo(0.0);
        // 15,000 tokens at $1/million = $0.015 - rounds up to $0.02 under HALF_UP.
        assertThat(ChatCostCalculator.roundToCents(15_000, 0, 1.0, 5.0)).isEqualTo(0.02);
    }

    @Test
    void zeroTokensCostNothing() {
        assertThat(ChatCostCalculator.roundToCents(0, 0, 1.0, 5.0)).isEqualTo(0.0);
    }
}
