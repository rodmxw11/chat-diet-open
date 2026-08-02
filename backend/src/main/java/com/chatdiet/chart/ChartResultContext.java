package com.chatdiet.chart;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.Optional;

/** Holds the computed chart series for the current chat HTTP request, if show_chart was invoked. */
@Component
@RequestScope
public class ChartResultContext {

    private List<ChartSeries> series;

    public void setSeries(List<ChartSeries> series) {
        this.series = series;
    }

    public Optional<List<ChartSeries>> series() {
        return Optional.ofNullable(series);
    }
}
