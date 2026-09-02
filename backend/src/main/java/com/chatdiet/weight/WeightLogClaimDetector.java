package com.chatdiet.weight;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristic for "does this reply read like a weight-logging confirmation" - used by
 * {@code ChatService} to decide whether to double-check that {@code log_weight} actually ran,
 * mirroring {@link com.chatdiet.food.FoodLogClaimDetector}'s reasoning for food.
 *
 * <p>The model's actual phrasing varies turn to turn ("Logged weight: 180.5 lbs.", "Logged 178.2
 * lbs.", "Logged: 179.4 lbs.") rather than reusing {@link LogWeightTool}'s exact echo text, so this
 * matches loosely: the word "logged" plus a number-and-unit mention. {@link CorrectWeightEntryTool}
 * replies ("Corrected weight: ...") don't say "logged", so they're not flagged - a correction
 * overwrites an existing row rather than needing a fresh one verified. Deliberately loose otherwise,
 * same rationale as the food detector: a false positive just costs one retry, a false negative lets
 * a genuinely unpersisted claim through unnoticed.
 */
public final class WeightLogClaimDetector {

    private static final Pattern WEIGHT_MENTION =
            Pattern.compile("(?i)\\d+(\\.\\d+)?\\s*(lbs?|pounds?|kgs?|kilograms?)\\b");

    private WeightLogClaimDetector() {
    }

    public static boolean looksLikeWeightLogClaim(String reply) {
        return reply.toLowerCase(Locale.ROOT).contains("logged") && WEIGHT_MENTION.matcher(reply).find();
    }
}
