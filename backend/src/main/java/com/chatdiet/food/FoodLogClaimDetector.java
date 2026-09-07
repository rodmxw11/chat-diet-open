package com.chatdiet.food;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristic for "does this reply read like a food-logging confirmation" - used by
 * {@code ChatService} to decide whether to double-check that a logging tool actually ran, given a
 * real, observed failure mode where the model narrates a plausible "Logged: ..., N kcal" reply
 * without ever invoking {@code log_food}/{@code log_food_by_upc}.
 *
 * <p>Two ways to qualify. A calorie mention plus at least two of the three macros is always a
 * claim - every food-log reply that lists macros mentions all three, unlike weight/vitals/exercise/
 * digestive-event confirmations, which never mention macros even when they do mention calories
 * (e.g. "320 kcal burned" for exercise). But plenty of genuine food-log replies are terser than
 * that - "Logged: applesauce, 100 cal (estimate)." mentions no macros at all - so a calorie mention
 * alongside the word "logged" also qualifies, unless it carries a marker word identifying it as a
 * different kind of confirmation or report instead (an exercise/goal/target reply, or a listing of
 * already-logged entries). Deliberately loose otherwise: a false positive just costs one extra
 * corrective round-trip, while a false negative lets a genuinely unpersisted claim slip through
 * unnoticed - so this errs toward flagging.
 */
public final class FoodLogClaimDetector {

    private static final Pattern CALORIE_MENTION = Pattern.compile("(?i)\\bk?cal(orie)?s?\\b");
    private static final String[] MACRO_WORDS = {"protein", "carb", "fat"};
    private static final String[] NON_FOOD_LOG_MARKERS = {
            "burned", "total:", "consumed", "remaining", "tdee", "kcal/day"
    };

    private FoodLogClaimDetector() {
    }

    public static boolean looksLikeFoodLogClaim(String reply) {
        if (!CALORIE_MENTION.matcher(reply).find()) {
            return false;
        }
        var lower = reply.toLowerCase(Locale.ROOT);
        if (Arrays.stream(MACRO_WORDS).filter(lower::contains).count() >= 2) {
            return true;
        }
        return lower.contains("logged") && Arrays.stream(NON_FOOD_LOG_MARKERS).noneMatch(lower::contains);
    }
}
