package com.chatdiet.food;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a single logged food entry.
 *
 * @param rawUtterance    the food description as logged (user's words, UPC lookup text, etc.)
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
        Integer prepMinutes,
        String source,
        LocalDateTime correctedAt,
        String priorValuesJson
) {

    @PersistenceCreator
    public FoodEntry {
    }

    public FoodEntry(LocalDateTime loggedAt, String rawUtterance, Integer totalCalories,
                      Double totalProteinG, Double totalCarbsG, Double totalFatG, String source) {
        this(null, loggedAt, rawUtterance, totalCalories, totalProteinG, totalCarbsG, totalFatG,
                null, source, null, null);
    }

    /**
     * Returns a copy of this entry with the given non-null fields replacing the current
     * calories/macros, stamping {@code correctedAt} to now and retaining {@code priorValuesJson}
     * as the pre-correction snapshot.
     *
     * @param priorValuesJson JSON snapshot of this entry's values before the correction, to be
     *                        stored for audit purposes
     */
    public FoodEntry corrected(Integer newTotalCalories, Double newTotalProteinG,
                                Double newTotalCarbsG, Double newTotalFatG, String priorValuesJson) {
        return new FoodEntry(id, loggedAt, rawUtterance,
                newTotalCalories != null ? newTotalCalories : totalCalories,
                newTotalProteinG != null ? newTotalProteinG : totalProteinG,
                newTotalCarbsG != null ? newTotalCarbsG : totalCarbsG,
                newTotalFatG != null ? newTotalFatG : totalFatG,
                prepMinutes, source, LocalDateTime.now(), priorValuesJson);
    }
}
