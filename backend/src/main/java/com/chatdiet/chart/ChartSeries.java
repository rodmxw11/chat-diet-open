package com.chatdiet.chart;

import java.util.List;

/** One named line of a chart (e.g. "Calories", "Protein (g)"), as computed by {@link ChartService}. */
public record ChartSeries(String label, List<SeriesPoint> points) {
}
