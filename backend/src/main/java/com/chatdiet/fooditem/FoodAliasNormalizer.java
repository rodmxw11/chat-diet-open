package com.chatdiet.fooditem;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a typed food phrase into a lookup key for {@link FoodAlias}. Every transformation applied
 * here is an assertion that two strings mean the same food - the same judgment the old substring
 * matcher made badly. It's safe here only because it's symmetric and total: applied identically to
 * the alias at write time and the query at read time, with no ranking and no candidate selection.
 *
 * <p>Deliberately narrow. Not stemmed ("oat" vs "oats" are different foods - the naive plural flip
 * is offered as a candidate to confirm once, not silently folded here), not stripped of
 * preparation words ("cooked rice" vs "rice" differ ~3x per 100g by water content) or brand words
 * ("great value smoked ham" is a specific product, not "smoked ham") or general stop words (only a
 * single leading article is dropped). An over-aggressive normalizer is a fuzzy matcher wearing a
 * different hat; bias toward under-normalizing.
 */
public final class FoodAliasNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern APOSTROPHES = Pattern.compile("['’]");
    private static final Pattern AMPERSAND = Pattern.compile("&");
    private static final Pattern SEPARATORS = Pattern.compile("[-_/]");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(a|an|the)\\s+");

    private FoodAliasNormalizer() {
    }

    /** Applies the full normalization rule set, in order, to produce a lookup key. */
    public static String normalize(String phrase) {
        var s = Normalizer.normalize(phrase, Normalizer.Form.NFKD);
        s = COMBINING_MARKS.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);
        s = APOSTROPHES.matcher(s).replaceAll("");
        s = AMPERSAND.matcher(s).replaceAll(" and ");
        s = SEPARATORS.matcher(s).replaceAll(" ");
        s = NON_WORD.matcher(s).replaceAll("");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        s = LEADING_ARTICLE.matcher(s).replaceFirst("");
        return s;
    }
}
