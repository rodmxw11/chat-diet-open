package com.chatdiet.food;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristic for "does this reply read like a food-logging confirmation" - used by
 * {@code ChatService} to decide whether to double-check that a logging tool actually ran, given a
 * real, observed failure mode where the model narrates a plausible "Logged: ..., N kcal" reply
 * without ever invoking {@code log_food}/{@code log_food_by_upc}/{@code log_cached_food}.
 *
 * <p>Requires both a calorie mention and at least two of the three macros, since every food-log
 * reply mentions all three - unlike weight/vitals/exercise/digestive-event confirmations, which
 * never mention macros even when they do mention calories (e.g. "320 kcal burned" for exercise).
 * Deliberately loose otherwise: a false positive just costs one extra corrective round-trip, while
 * a false negative lets a genuinely unpersisted claim slip through unnoticed - so this errs toward
 * flagging.
 */
public final class FoodLogClaimDetector {

    private static final Pattern CALORIE_MENTION = Pattern.compile("(?i)\\bk?cal(orie)?s?\\b");
    private static final String[] MACRO_WORDS = {"protein", "carb", "fat"};

    private FoodLogClaimDetector() {
    }

    public static boolean looksLikeFoodLogClaim(String reply) {
        if (!CALORIE_MENTION.matcher(reply).find()) {
            return false;
        }
        var lower = reply.toLowerCase(Locale.ROOT);
        return Arrays.stream(MACRO_WORDS).filter(lower::contains).count() >= 2;
    }
}
