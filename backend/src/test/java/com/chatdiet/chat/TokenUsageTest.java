package com.chatdiet.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageTest {

    @Test
    void plusSumsEachField() {
        var first = new TokenUsage(100, 20, 120);
        var second = new TokenUsage(200, 30, 230);

        var sum = first.plus(second);

        assertThat(sum.promptTokens()).isEqualTo(300);
        assertThat(sum.completionTokens()).isEqualTo(50);
        assertThat(sum.totalTokens()).isEqualTo(350);
    }

    @Test
    void plusTreatsNullFieldsAsZero() {
        var withNulls = new TokenUsage(null, 20, null);
        var full = new TokenUsage(100, 30, 130);

        var sum = withNulls.plus(full);

        assertThat(sum.promptTokens()).isEqualTo(100);
        assertThat(sum.completionTokens()).isEqualTo(50);
        assertThat(sum.totalTokens()).isEqualTo(130);
    }
}
