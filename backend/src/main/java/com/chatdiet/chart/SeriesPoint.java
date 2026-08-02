package com.chatdiet.chart;

import java.time.Instant;

public record SeriesPoint(Instant at, double value, Double target) {
}
