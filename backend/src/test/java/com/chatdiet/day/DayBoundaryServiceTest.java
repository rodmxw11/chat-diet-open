package com.chatdiet.day;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DayBoundaryServiceTest {

    private static final int ROLLOVER_HOUR = 4;

    private DayBoundaryService dayBoundaryService;

    @BeforeEach
    void newService() {
        dayBoundaryService = new DayBoundaryService();
        ReflectionTestUtils.setField(dayBoundaryService, "dayRolloverHour", ROLLOVER_HOUR);
    }

    @Test
    void lateNightBelongsToThePreviousDayAndEarlyMorningStartsTheNewOne() {
        assertThat(dayBoundaryService.metabolicDateOf(LocalDateTime.of(2026, 8, 9, 3, 59)))
                .as("before the 4am rollover is still the previous metabolic day")
                .isEqualTo(LocalDate.of(2026, 8, 8));
        assertThat(dayBoundaryService.metabolicDateOf(LocalDateTime.of(2026, 8, 9, 4, 1)))
                .isEqualTo(LocalDate.of(2026, 8, 9));
    }

    /**
     * Clients send UTC instants, so the conversion has to go through the server's zone. Skipping
     * that would silently file every message into the wrong day outside UTC.
     */
    @Test
    void instantsAreInterpretedInTheServersZone() {
        var localMidAfternoon = LocalDateTime.of(2026, 8, 9, 15, 30);
        var asInstant = localMidAfternoon.atZone(ZoneId.systemDefault()).toInstant();

        assertThat(dayBoundaryService.metabolicDateOf(asInstant))
                .isEqualTo(dayBoundaryService.metabolicDateOf(localMidAfternoon));
    }

    @Test
    void aTimestampFromTheOfflineQueueIsTrusted() {
        var composedEarlier = LocalDateTime.now().minusHours(6);
        var clientSentAt = composedEarlier.atZone(ZoneId.systemDefault()).toInstant().toString();

        assertThat(dayBoundaryService.occurredAt(clientSentAt))
                .isCloseTo(composedEarlier, within(1, ChronoUnit.SECONDS));
    }

    /** A device with a fast clock must not write rows dated in the future - they'd never show up. */
    @Test
    void aFutureTimestampIsClampedToNow() {
        var clientSentAt = Instant.now().plusSeconds(2 * 24 * 60 * 60).toString();

        assertThat(dayBoundaryService.occurredAt(clientSentAt))
                .isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(dayBoundaryService.metabolicDateOf(dayBoundaryService.occurredAt(clientSentAt)))
                .isEqualTo(dayBoundaryService.today());
    }

    @Test
    void missingOrMalformedTimestampsFallBackToNow() {
        assertThat(dayBoundaryService.occurredAt(null))
                .isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.SECONDS));
        assertThat(dayBoundaryService.occurredAt("not-a-timestamp"))
                .isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.SECONDS));
    }
}
