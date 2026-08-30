package com.chatdiet.food.resolve;

import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.PortionUnitService;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Resolves how much of a {@link FoodItem} was eaten, in grams, from an amount phrase or an
 * explicit grams/servings pair. Tiers: an explicit gram amount, optionally with a leading
 * count+unit alongside it for teaching ("2 eggs, 100g"); a bare serving count scaled by the item's
 * {@code typical_serving_g}; a count plus a natural unit resolved against {@code PORTION_UNIT}
 * ("2 medium", "a bowl"). Unparseable or empty input is {@link QuantityResolution.Unresolvable} -
 * never a guess.
 */
@Service
public class QuantityResolver {

    private static final Pattern TRAILING_GRAMS =
            Pattern.compile("(?i)^(.*?)\\s*,?\\s*(?:about\\s+)?([0-9]*\\.?[0-9]+)\\s*g(?:rams?)?$");
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

    /** For typed/spoken UPCs, which carry an explicit grams-or-servings pair rather than a phrase. */
    public QuantityResolution resolve(FoodItem item, Double quantityG, Double quantityServings) {
        if (quantityG != null && quantityG > 0) {
            return new QuantityResolution.Grams(quantityG);
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
