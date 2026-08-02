package com.chatdiet.day;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Defines the app's "metabolic day" concept, used throughout the app (e.g. daily nutrition
 * totals, fasting windows) in place of the calendar day. A metabolic day does not roll over at
 * midnight; instead it rolls over at a configurable hour (default 4am, {@code
 * chat-diet.day-rollover-hour}), so that eating/activity that happens shortly after midnight is
 * still attributed to the previous calendar day rather than starting a new one.
 */
@Service
public class DayBoundaryService {

    @Value("${chat-diet.day-rollover-hour:4}")
    private int dayRolloverHour;

    /**
     * Maps a timestamp to the metabolic date it belongs to: timestamps before the rollover hour
     * belong to the previous calendar day.
     */
    public LocalDate metabolicDateOf(LocalDateTime timestamp) {
        return timestamp.getHour() < dayRolloverHour
                ? timestamp.toLocalDate().minusDays(1)
                : timestamp.toLocalDate();
    }

    /** Returns the timestamp at which the given metabolic date begins (the rollover hour of that calendar date). */
    public LocalDateTime startOfMetabolicDay(LocalDate metabolicDate) {
        return metabolicDate.atTime(dayRolloverHour, 0);
    }

    /** Returns the timestamp at which the given metabolic date ends (the rollover hour of the following calendar date). */
    public LocalDateTime endOfMetabolicDay(LocalDate metabolicDate) {
        return startOfMetabolicDay(metabolicDate.plusDays(1));
    }

    /** Returns the current metabolic date, i.e. {@link #metabolicDateOf} applied to now. */
    public LocalDate today() {
        return metabolicDateOf(LocalDateTime.now());
    }
}
