package com.chatdiet.food;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

public record FoodEntry(
        @Id Long id,
        LocalDateTime loggedAt,
        String rawUtterance,
        Integer totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG,
        Double costUsd,
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
                null, null, source, null, null);
    }

    public FoodEntry corrected(Integer newTotalCalories, Double newTotalProteinG,
                                Double newTotalCarbsG, Double newTotalFatG, String priorValuesJson) {
        return new FoodEntry(id, loggedAt, rawUtterance,
                newTotalCalories != null ? newTotalCalories : totalCalories,
                newTotalProteinG != null ? newTotalProteinG : totalProteinG,
                newTotalCarbsG != null ? newTotalCarbsG : totalCarbsG,
                newTotalFatG != null ? newTotalFatG : totalFatG,
                costUsd, prepMinutes, source, LocalDateTime.now(), priorValuesJson);
    }
}
