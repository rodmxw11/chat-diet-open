package com.chatdiet.vitals;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a single blood pressure / heart rate reading.
 *
 * @param heartRate   optional; may be {@code null} if only blood pressure was recorded
 * @param correctedAt {@code null} unless this entry has since been amended via
 *                     {@link CorrectVitalsEntryTool}, in which case it holds the time of the
 *                     most recent correction
 */
public record VitalsEntry(
        @Id Long id,
        LocalDateTime loggedAt,
        Integer systolic,
        Integer diastolic,
        Integer heartRate,
        LocalDateTime correctedAt
) {

    @PersistenceCreator
    public VitalsEntry {
    }

    public VitalsEntry(LocalDateTime loggedAt, Integer systolic, Integer diastolic, Integer heartRate) {
        this(null, loggedAt, systolic, diastolic, heartRate, null);
    }

    /**
     * Returns a copy of this entry with any non-null argument overriding the current value, and
     * {@code correctedAt} stamped to now.
     */
    public VitalsEntry corrected(Integer newSystolic, Integer newDiastolic, Integer newHeartRate) {
        return new VitalsEntry(id, loggedAt,
                newSystolic != null ? newSystolic : systolic,
                newDiastolic != null ? newDiastolic : diastolic,
                newHeartRate != null ? newHeartRate : heartRate,
                LocalDateTime.now());
    }
}
