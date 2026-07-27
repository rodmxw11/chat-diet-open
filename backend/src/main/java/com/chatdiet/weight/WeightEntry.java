package com.chatdiet.weight;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

public record WeightEntry(
        @Id Long id,
        LocalDateTime loggedAt,
        Double weightLbs,
        LocalDateTime correctedAt
) {

    @PersistenceCreator
    public WeightEntry {
    }

    public WeightEntry(LocalDateTime loggedAt, Double weightLbs) {
        this(null, loggedAt, weightLbs, null);
    }

    public WeightEntry corrected(double newWeightLbs) {
        return new WeightEntry(id, loggedAt, newWeightLbs, LocalDateTime.now());
    }
}
