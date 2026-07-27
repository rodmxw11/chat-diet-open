package com.chatdiet.vitals;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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

    public VitalsEntry corrected(Integer newSystolic, Integer newDiastolic, Integer newHeartRate) {
        return new VitalsEntry(id, loggedAt,
                newSystolic != null ? newSystolic : systolic,
                newDiastolic != null ? newDiastolic : diastolic,
                newHeartRate != null ? newHeartRate : heartRate,
                LocalDateTime.now());
    }
}
