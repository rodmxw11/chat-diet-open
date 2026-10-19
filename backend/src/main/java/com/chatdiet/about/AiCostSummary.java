package com.chatdiet.about;

import java.time.LocalDate;

/**
 * Approximate AI model spend over a trailing window of metabolic days, from the token usage
 * recorded on each chat turn and the flat rates in {@code model_pricing} - the same figures the
 * chat history page shows per day, summed. Each model is rounded to the cent.
 *
 * @param fromDate     first metabolic day counted, inclusive
 * @param toDate       last metabolic day counted (today), inclusive
 * @param haikuCostUsd main chat model cost
 * @param opusCostUsd  SQL-composer subchat cost
 */
public record AiCostSummary(
        LocalDate fromDate,
        LocalDate toDate,
        double haikuCostUsd,
        double opusCostUsd
) {
}
