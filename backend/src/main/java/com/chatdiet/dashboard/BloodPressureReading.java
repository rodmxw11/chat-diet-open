package com.chatdiet.dashboard;

import java.time.LocalDateTime;

/** One blood-pressure/heart-rate reading for the blood pressure chart. */
public record BloodPressureReading(LocalDateTime timestamp, int systolic, int diastolic, Integer bpm) {
}
