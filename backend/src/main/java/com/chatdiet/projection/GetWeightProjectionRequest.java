package com.chatdiet.projection;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Request DTO for {@link GetWeightProjectionTool}. Exactly one of {@code goalWeightLbs} or
 * {@code goalDate} is expected to be provided.
 *
 * <p>{@code goalDate} is a String, not a LocalDate: some models fill an unwanted optional field
 * with a placeholder like "invalid" rather than omitting it, which would otherwise fail
 * deserialization before the tool ever runs.
 *
 * @param goalDate ISO-8601 date string, parsed leniently via {@link #parsedGoalDate()}
 */
public record GetWeightProjectionRequest(Double goalWeightLbs, String goalDate) {

    /** Parses {@link #goalDate()}, returning {@code null} instead of throwing if it is absent or malformed. */
    LocalDate parsedGoalDate() {
        if (goalDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(goalDate);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
