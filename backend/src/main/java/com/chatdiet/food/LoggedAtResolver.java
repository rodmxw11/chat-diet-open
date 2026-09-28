package com.chatdiet.food;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Resolves the timestamp a food entry should be logged under, given an optional model-supplied
 * override for backdated entries (e.g. "I ate a hamburger yesterday for dinner").
 */
public final class LoggedAtResolver {

    /**
     * Where a bare date lands: midday is safely inside the metabolic day whatever the rollover
     * hour, and a named meal still gets its own time (breakfast 8:00, dinner 18:00) from the prompt.
     */
    static final LocalTime DATE_ONLY_TIME = LocalTime.NOON;

    private LoggedAtResolver() {
    }

    /**
     * Parses the model's {@code loggedAt} leniently. It arrives as free text, and a strict
     * {@code LocalDateTime} field used to fail JSON conversion outright on a bare date
     * ("2026-09-27" for "yesterday") - which crashed the whole chat request, since conversion
     * happens before the tool runs. Accepts an ISO local date-time (with or without seconds, 'T' or
     * a space), one with an offset or 'Z' (converted to local time), or a bare date (at noon).
     *
     * @return null when absent - log under now
     * @throws IllegalArgumentException when present but unparseable. Deliberately not a silent
     *         fallback to now: that would quietly file yesterday's food under today. The tool
     *         call fails instead, and the model sees the message and retries with a valid value.
     */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var trimmed = text.strip().replace(' ', 'T');
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // not a local date-time - try the other accepted shapes
        }
        try {
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // no offset either
        }
        try {
            return LocalDate.parse(trimmed).atTime(DATE_ONLY_TIME);
        } catch (DateTimeParseException ignored) {
            throw new IllegalArgumentException("loggedAt \"" + text + "\" isn't a date or date-time - "
                    + "use ISO-8601 local time like 2026-09-27T12:30 (a date alone logs at noon)");
        }
    }

    /**
     * @param requested a model-supplied timestamp, or null to log under now
     * @return {@code requested} if present and not in the future, otherwise now - a future
     *         timestamp is treated as a model mistake rather than trusted, the same way
     *         {@link com.chatdiet.day.DayBoundaryService#occurredAt} treats a future client clock
     */
    public static LocalDateTime resolve(LocalDateTime requested) {
        var now = LocalDateTime.now();
        if (requested == null || requested.isAfter(now)) {
            return now;
        }
        return requested;
    }
}
