package com.chatdiet.dashboard;

import java.time.LocalDate;

/** One day's Hacker's-Diet exponentially-smoothed weight trend value. */
public record TrendPoint(LocalDate date, double value) {
}
