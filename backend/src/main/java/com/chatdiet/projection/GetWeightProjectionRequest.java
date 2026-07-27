package com.chatdiet.projection;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * goalDate is a String, not a LocalDate: some models fill an unwanted optional field with a
 * placeholder like "invalid" rather than omitting it, which would otherwise fail deserialization
 * before the tool ever runs.
 */
public record GetWeightProjectionRequest(Double goalWeightLbs, String goalDate) {

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
