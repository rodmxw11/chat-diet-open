package com.chatdiet.food;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a single logged food entry.
 *
 * @param rawUtterance    the food description as logged (user's words, UPC lookup text, etc.)
 * @param fiberG          estimated dietary fiber in grams, or {@code null} if unknown/not estimated
 * @param sugarG          estimated total sugar in grams, or {@code null} if unknown/not estimated
 * @param sodiumMg        estimated sodium in milligrams, or {@code null} if unknown/not estimated
 * @param saturatedFatG   estimated saturated fat in grams, or {@code null} if unknown/not estimated
 * @param cholesterolMg   estimated cholesterol in milligrams, or {@code null} if unknown/not estimated
 * @param potassiumMg     estimated potassium in milligrams, or {@code null} if unknown/not estimated
 * @param prepMinutes     optional prep time in minutes, if known; typically {@code null}
 * @param source          origin of this entry, e.g. {@code "MANUAL"} or a UPC/cached-food source tag
 * @param correctedAt     timestamp of the most recent correction via {@link #corrected}, or
 *                        {@code null} if never corrected
 * @param priorValuesJson JSON snapshot of this entry's values immediately before the most
 *                        recent correction, or {@code null} if never corrected
 */
public record FoodEntry(
        @Id Long id,
        LocalDateTime loggedAt,
        String rawUtterance,
        Integer totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG,
        Double fiberG,
        Double sugarG,
        Double sodiumMg,
        Double saturatedFatG,
        Double cholesterolMg,
        Double potassiumMg,
        Integer prepMinutes,
        String source,
        LocalDateTime correctedAt,
        String priorValuesJson
) {

    @PersistenceCreator
    public FoodEntry {
    }

    /** Convenience constructor for entries with no micronutrient data (e.g. test fixtures). */
    public FoodEntry(LocalDateTime loggedAt, String rawUtterance, Integer totalCalories,
                      Double totalProteinG, Double totalCarbsG, Double totalFatG, String source) {
        this(null, loggedAt, rawUtterance, totalCalories, totalProteinG, totalCarbsG, totalFatG,
                null, null, null, null, null, null, null, source, null, null);
    }

    /** Convenience constructor for a freshly logged entry, including estimated micronutrients. */
    public FoodEntry(LocalDateTime loggedAt, String rawUtterance, Integer totalCalories,
                      Double totalProteinG, Double totalCarbsG, Double totalFatG,
                      Double fiberG, Double sugarG, Double sodiumMg, Double saturatedFatG,
                      Double cholesterolMg, Double potassiumMg, String source) {
        this(null, loggedAt, rawUtterance, totalCalories, totalProteinG, totalCarbsG, totalFatG,
                fiberG, sugarG, sodiumMg, saturatedFatG, cholesterolMg, potassiumMg,
                null, source, null, null);
    }

    /**
     * Returns a copy of this entry with the given non-null fields replacing the current
     * calories/macros/micronutrients, stamping {@code correctedAt} to now and retaining
     * {@code priorValuesJson} as the pre-correction snapshot.
     *
     * @param priorValuesJson JSON snapshot of this entry's values before the correction, to be
     *                        stored for audit purposes
     */
    public FoodEntry corrected(Integer newTotalCalories, Double newTotalProteinG,
                                Double newTotalCarbsG, Double newTotalFatG,
                                Double newFiberG, Double newSugarG, Double newSodiumMg,
                                Double newSaturatedFatG, Double newCholesterolMg, Double newPotassiumMg,
                                String priorValuesJson) {
        return new FoodEntry(id, loggedAt, rawUtterance,
                newTotalCalories != null ? newTotalCalories : totalCalories,
                newTotalProteinG != null ? newTotalProteinG : totalProteinG,
                newTotalCarbsG != null ? newTotalCarbsG : totalCarbsG,
                newTotalFatG != null ? newTotalFatG : totalFatG,
                newFiberG != null ? newFiberG : fiberG,
                newSugarG != null ? newSugarG : sugarG,
                newSodiumMg != null ? newSodiumMg : sodiumMg,
                newSaturatedFatG != null ? newSaturatedFatG : saturatedFatG,
                newCholesterolMg != null ? newCholesterolMg : cholesterolMg,
                newPotassiumMg != null ? newPotassiumMg : potassiumMg,
                prepMinutes, source, LocalDateTime.now(), priorValuesJson);
    }
}
