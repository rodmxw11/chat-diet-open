package com.chatdiet.chart;

import java.time.Instant;

/**
 * One data point on a {@link ChartSeries}.
 *
 * @param target the goal value at this point, or null when the series has no goal overlay
 *                (see {@link ChartRequest#includeGoal()})
 */
public record SeriesPoint(Instant at, double value, Double target) {
}
