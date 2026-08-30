package com.chatdiet.fooditem;

import com.chatdiet.fdc.FdcPortion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Resolves and grows a {@link FoodItem}'s known unit-to-grams mappings ("medium" -> 118g), so a
 * natural quantity ("1 medium banana") can scale deterministically instead of needing a stated
 * gram amount every time. Two ways portions get learned: from USDA FDC's own {@code
 * foodPortions} (fetched once per newly FDC-cached item), and from the user's own weighed
 * entries that state both a quantity+unit and a gram amount in the same breath.
 */
@Service
public class PortionUnitService {

    private final PortionUnitRepository portionUnitRepository;

    public PortionUnitService(PortionUnitRepository portionUnitRepository) {
        this.portionUnitRepository = portionUnitRepository;
    }

    /**
     * Resolves a stated unit (e.g. "medium") to grams for the given cached item, if known.
     * Prefers an exact normalized match; falls back to a bidirectional-substring match (same
     * tolerance style as food-name matching) only when exactly one candidate matches that way -
     * FDC often reports several preparations of the same rough unit ("cup, sliced" vs.
     * "cup, mashed" both contain "cup"), and guessing between them would silently pick whichever
     * happened to come first rather than admitting the unit is ambiguous.
     */
    public OptionalDouble resolveGrams(long foodItemId, String unit) {
        var normalized = normalize(unit);
        var known = portionUnitRepository.findByFoodItemId(foodItemId);

        var exact = known.stream().filter(p -> p.unitName().equals(normalized)).findFirst();
        if (exact.isPresent()) {
            return OptionalDouble.of(exact.get().grams());
        }

        var substringMatches = known.stream()
                .filter(p -> p.unitName().contains(normalized) || normalized.contains(p.unitName()))
                .toList();
        return substringMatches.size() == 1 ? OptionalDouble.of(substringMatches.get(0).grams()) : OptionalDouble.empty();
    }

    /** Stores every portion USDA reported for the given FDC food (fetched by the caller's detail call), for future lookups. */
    public void storePortions(long foodItemId, List<FdcPortion> portions) {
        for (var portion : portions) {
            upsert(foodItemId, normalize(portion.unitName()), portion.grams(), "FDC");
        }
    }

    /** Records grams-per-unit learned from a weighed entry that stated both a unit and a gram amount. */
    public void learnFromWeighedEntry(long foodItemId, String unit, double quantity, double amountGrams) {
        if (quantity <= 0) {
            return;
        }
        upsert(foodItemId, normalize(unit), amountGrams / quantity, "WEIGHED");
    }

    /** Records a household measure entered directly on the Food Items page (e.g. "slice" -> 43g). */
    public void recordManualPortion(long foodItemId, String unit, double grams) {
        upsert(foodItemId, normalize(unit), grams, "MANUAL");
    }

    private void upsert(long foodItemId, String unitName, double grams, String source) {
        var existing = portionUnitRepository.findByFoodItemIdAndUnitName(foodItemId, unitName).orElse(null);
        var id = existing != null ? existing.id() : null;
        portionUnitRepository.save(new PortionUnit(id, foodItemId, unitName, grams, source));
    }

    /**
     * FDC modifiers look like {@code "medium (7\" to 7-7/8\" long)"} - keep only the descriptor
     * before any parenthetical detail, lowercased and trimmed, so both FDC-sourced and
     * user-stated unit words compare on equal footing.
     */
    static String normalize(String unit) {
        var cut = unit.indexOf('(');
        var base = cut >= 0 ? unit.substring(0, cut) : unit;
        return base.toLowerCase(Locale.ROOT).trim();
    }
}
