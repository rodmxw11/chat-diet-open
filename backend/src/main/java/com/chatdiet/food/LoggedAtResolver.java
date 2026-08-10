package com.chatdiet.food;

import java.time.LocalDateTime;

/**
 * Resolves the timestamp a food entry should be logged under, given an optional model-supplied
 * override for backdated entries (e.g. "I ate a hamburger yesterday for dinner").
 */
public final class LoggedAtResolver {

    private LoggedAtResolver() {
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
