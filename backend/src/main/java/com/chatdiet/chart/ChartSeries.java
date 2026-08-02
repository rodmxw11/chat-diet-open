package com.chatdiet.chart;

import java.util.List;

public record ChartSeries(String label, List<SeriesPoint> points) {
}
