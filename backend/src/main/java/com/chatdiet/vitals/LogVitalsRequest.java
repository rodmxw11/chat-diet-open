package com.chatdiet.vitals;

/**
 * Request DTO for {@link LogVitalsTool}. {@code heartRate} is optional (nullable); systolic and
 * diastolic pressure are required.
 */
public record LogVitalsRequest(int systolic, int diastolic, Integer heartRate) {
}
