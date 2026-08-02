package com.chatdiet.vitals;

/**
 * Request DTO for {@link CorrectVitalsEntryTool}. Any field left {@code null} means "leave this
 * value unchanged" on the existing entry, rather than "clear it".
 */
public record CorrectVitalsRequest(Integer systolic, Integer diastolic, Integer heartRate) {
}
