package com.chatdiet.food.resolve;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes the handful of quick food-entry shapes that can be logged without the model, split
 * into an amount phrase and a food phrase. The grammar is corpus-informed - of the real logged
 * history, "I ate 207 g of greek yogurt", "I ate 100 cal apple sauce", and "3 slices honey wheat
 * bread" are the shapes that recur - so an optional leading "I ate"/"ate" is stripped and the
 * rest must open with a number. Everything else returns empty and flows to the model untouched:
 * "yesterday ..." (backdating), corrections, multi-item sentences, estimate-style trailing
 * numbers, bare clarification-pick digits.
 */
public final class FastLogParser {

    private static final Pattern LEADING_ATE = Pattern.compile("(?i)^(?:i\\s+)?ate\\s+");
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[.!?\\s]+$");
    private static final Pattern GRAMS_FIRST =
            Pattern.compile("(?i)^([0-9]*\\.?[0-9]+)\\s*g(?:rams?)?\\s+(?:of\\s+)?(\\S.*)$");
    private static final Pattern KCAL_FIRST =
            Pattern.compile("(?i)^([0-9]*\\.?[0-9]+)\\s*k?cal(?:orie)?s?\\s+(?:of\\s+)?(\\S.*)$");
    private static final Pattern COUNT_FIRST = Pattern.compile("^([0-9]*\\.?[0-9]+)\\s+(\\S.*)$");

    private FastLogParser() {
    }

    /**
     * A recognized quick entry.
     *
     * @param amountPhrase the amount in {@link QuantityResolver}-ready form ("142g", "250 cal",
     *                     or a bare count)
     * @param foodPhrase   the remaining food words, to be resolved as an exact alias
     * @param bareCount    true for the count form, where the first food word may actually be a
     *                     portion unit ("3 slices honey wheat bread") - the caller may retry with
     *                     that split if the whole phrase isn't an alias
     */
    public record Parsed(String amountPhrase, String foodPhrase, boolean bareCount) {
    }

    public static Optional<Parsed> parse(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        var text = TRAILING_PUNCT.matcher(message.trim()).replaceFirst("");
        text = LEADING_ATE.matcher(text).replaceFirst("");

        var grams = GRAMS_FIRST.matcher(text);
        if (grams.matches()) {
            return Optional.of(new Parsed(grams.group(1) + "g", grams.group(2).trim(), false));
        }
        var kcal = KCAL_FIRST.matcher(text);
        if (kcal.matches()) {
            return Optional.of(new Parsed(kcal.group(1) + " cal", kcal.group(2).trim(), false));
        }
        var count = COUNT_FIRST.matcher(text);
        if (count.matches()) {
            return Optional.of(new Parsed(count.group(1), count.group(2).trim(), true));
        }
        return Optional.empty();
    }
}