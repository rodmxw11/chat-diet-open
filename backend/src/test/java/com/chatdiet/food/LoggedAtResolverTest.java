package com.chatdiet.food;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggedAtResolverTest {

    /** The exact value that crashed "yesterday i ate 10 oz orange juice" on 2026-09-28. */
    @Test
    void aBareDateLogsAtNoon() {
        assertThat(LoggedAtResolver.parse("2026-09-27")).isEqualTo(LocalDateTime.of(2026, 9, 27, 12, 0));
    }

    @Test
    void acceptsLocalDateTimesWithOrWithoutSecondsAndWithASpace() {
        assertThat(LoggedAtResolver.parse("2026-09-27T19:30")).isEqualTo(LocalDateTime.of(2026, 9, 27, 19, 30));
        assertThat(LoggedAtResolver.parse("2026-09-27T19:30:15")).isEqualTo(LocalDateTime.of(2026, 9, 27, 19, 30, 15));
        assertThat(LoggedAtResolver.parse(" 2026-09-27 08:00 ")).isEqualTo(LocalDateTime.of(2026, 9, 27, 8, 0));
    }

    @Test
    void convertsAnOffsetOrZuluTimeToLocalTime() {
        var expected = OffsetDateTime.parse("2026-09-27T22:00:00Z")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        assertThat(LoggedAtResolver.parse("2026-09-27T22:00:00Z")).isEqualTo(expected);
        assertThat(LoggedAtResolver.parse("2026-09-27T18:00:00-04:00"))
                .isEqualTo(OffsetDateTime.parse("2026-09-27T18:00:00-04:00")
                        .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
    }

    @Test
    void absentMeansNow() {
        assertThat(LoggedAtResolver.parse(null)).isNull();
        assertThat(LoggedAtResolver.parse("  ")).isNull();
    }

    /** Never a silent fallback to now - that would file yesterday's food under today. */
    @Test
    void anUnparseableValueIsAnErrorTheModelCanCorrect() {
        assertThatThrownBy(() -> LoggedAtResolver.parse("yesterday evening"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-09-27T12:30");
    }
}
