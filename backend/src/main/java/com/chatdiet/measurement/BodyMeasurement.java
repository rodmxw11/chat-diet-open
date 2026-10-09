package com.chatdiet.measurement;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * One tape-measurement session, in inches. Any value may be absent - a session can be waist alone -
 * and {@link MeasurementService} carries the latest neck and hip forward when estimating body fat.
 */
public record BodyMeasurement(
        @Id Long id,
        LocalDateTime measuredAt,
        Double waistIn,
        Double neckIn,
        Double hipIn
) {

    @PersistenceCreator
    public BodyMeasurement {
    }

    public BodyMeasurement(LocalDateTime measuredAt, Double waistIn, Double neckIn, Double hipIn) {
        this(null, measuredAt, waistIn, neckIn, hipIn);
    }
}
