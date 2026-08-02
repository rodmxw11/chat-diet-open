package com.chatdiet.weight;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * A single body weight reading.
 *
 * @param correctedAt when this entry's value was last overwritten via
 *                      {@code correct_weight_entry}, or null if it has never been corrected
 */
public record WeightEntry(
        @Id Long id,
        LocalDateTime loggedAt,
        Double weightLbs,
        LocalDateTime correctedAt
) {

    @PersistenceCreator
    public WeightEntry {
    }

    /** Creates a new, uncorrected entry. */
    public WeightEntry(LocalDateTime loggedAt, Double weightLbs) {
        this(null, loggedAt, weightLbs, null);
    }

    /** Returns a copy with the weight overwritten and {@code correctedAt} stamped to now. */
    public WeightEntry corrected(double newWeightLbs) {
        return new WeightEntry(id, loggedAt, newWeightLbs, LocalDateTime.now());
    }
}
