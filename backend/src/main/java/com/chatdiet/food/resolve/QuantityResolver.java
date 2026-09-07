package com.chatdiet.food.resolve;

import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.PortionUnitService;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Resolves how much of a {@link FoodItem} was eaten, in grams, from an amount phrase or an
 * explicit grams/kcal/servings value. Tiers: an explicit gram amount, optionally with a leading
 * count+unit alongside it for teaching ("2 eggs, 100g"); an explicit calorie amount ("250 cal")
 * inverted through the item's per-100g calories; a bare serving count scaled by the item's
 * {@code typical_serving_g}; a count plus a natural unit resolved against {@code PORTION_UNIT}
 * ("2 medium", "a bowl"). Unparseable or empty input is {@link QuantityResolution.Unresolvable} -
 * never a guess.
 */
@Service
public class QuantityResolver {

    private static final Pattern TRAILING_GRAMS =
            Pattern.compile("(?i)^(.*?)\\s*,?\\s*(?:about\\s+)?([0-9]*\\.?[0-9]+)\\s*g(?:rams?)?$");
    private static final Pattern EXPLICIT_KCAL =
            Pattern.compile("(?i)^(?:about\\s+)?([0-9]*\\.?[0-9]+)\\s*k?cal(?:orie)?s?$");
    private static final Pattern PURE_NUMBER = Pattern.compile("^[0-9]*\\.?[0-9]+$");
    private static final Pattern COUNT_AND_UNIT = Pattern.compile("^([0-9]*\\.?[0-9]+)?\\s*(.*)$");
    private static final Pattern LEADING_ARTICLE = Pattern.compile("(?i)^(a|an|the)\\s+");

    private final PortionUnitService portionUnitService;

    public QuantityResolver(PortionUnitService portionUnitService) {
        this.portionUnitService = portionUnitService;
    }

    /** Parses a free-text amount phrase against the given item's known portions/typical serving. */
    public QuantityResolution resolve(FoodItem item, String amountText) {
        if (amountText == null || amountText.isBlank()) {
            return new QuantityResolution.Unresolvable();
        }
        var trimmed = amountText.trim();

        var explicit = parseExplicitGrams(trimmed);
        if (explicit != null) {
            return explicit;
        }

        var kcalMatcher = EXPLICIT_KCAL.matcher(trimmed);
        if (kcalMatcher.matches()) {
            return kcalToGrams(item, Double.parseDouble(kcalMatcher.group(1)));
        }

        if (PURE_NUMBER.matcher(trimmed).matches()) {
            return resolveServings(item, Double.parseDouble(trimmed));
        }

        var countAndUnit = parseCountAndUnit(trimmed);
        if (countAndUnit != null) {
            var gramsPerUnit = portionUnitService.resolveGrams(item.id(), countAndUnit.unit());
            if (gramsPerUnit.isPresent()) {
                return new QuantityResolution.Grams(gramsPerUnit.getAsDouble() * countAndUnit.count());
            }
        }

        return new QuantityResolution.Unresolvable();
    }

    /**
     * For structured callers (typed/spoken UPCs, the scan quantity prompt), which carry exactly
     * one explicit grams/kcal/servings value rather than a phrase.
     */
    public QuantityResolution resolve(FoodItem item, Double quantityG, Double quantityServings,
                                       Double quantityKcal) {
        if (quantityG != null && quantityG > 0) {
            return new QuantityResolution.Grams(quantityG);
        }
        if (quantityKcal != null && quantityKcal > 0) {
            return kcalToGrams(item, quantityKcal);
        }
        if (quantityServings != null && quantityServings > 0) {
            return resolveServings(item, quantityServings);
        }
        return new QuantityResolution.Unresolvable();
    }

    private QuantityResolution resolveServings(FoodItem item, double servings) {
        var typical = item.typicalServingG() != null ? item.typicalServingG() : 100.0;
        return new QuantityResolution.Grams(servings * typical);
    }

    /**
     * Inverts a stated calorie amount into grams via the item's per-100g calories. Unresolvable
     * for an item with no usable calorie figure - the caller's grams question is the safe out.
     */
    private static QuantityResolution kcalToGrams(FoodItem item, double kcal) {
        if (item.per100gCalories() == null || item.per100gCalories() <= 0) {
            return new QuantityResolution.Unresolvable();
        }
        return new QuantityResolution.Grams(kcal / item.per100gCalories() * 100.0);
    }

    /**
     * Recognizes an explicit gram figure with no {@link FoodItem} to check against - usable
     * without a resolved item (e.g. a fresh model estimate), since it's just a literal number in
     * the phrase, optionally with a leading count+unit alongside it for teaching next time
     * ("2 eggs, 100g").
     */
    public static QuantityResolution.Grams parseExplicitGrams(String amountText) {
        if (amountText == null || amountText.isBlank()) {
            return null;
        }
        var m = TRAILING_GRAMS.matcher(amountText.trim());
        if (!m.matches()) {
            return null;
        }
        var grams = Double.parseDouble(m.group(2));
        var leading = m.group(1).trim();
        if (!leading.isEmpty()) {
            var teachUnit = parseCountAndUnit(leading);
            if (teachUnit != null) {
                return new QuantityResolution.Grams(grams, teachUnit.count(), teachUnit.unit());
            }
        }
        return new QuantityResolution.Grams(grams);
    }

    private record CountAndUnit(double count, String unit) {
    }

    private static CountAndUnit parseCountAndUnit(String text) {
        var m = COUNT_AND_UNIT.matcher(text);
        if (!m.matches()) {
            return null;
        }
        var unit = LEADING_ARTICLE.matcher(m.group(2) == null ? "" : m.group(2).trim()).replaceFirst("").trim();
        if (unit.isEmpty()) {
            return null;
        }
        var count = m.group(1) != null && !m.group(1).isEmpty() ? Double.parseDouble(m.group(1)) : 1.0;
        return new CountAndUnit(count, unit);
    }
}
