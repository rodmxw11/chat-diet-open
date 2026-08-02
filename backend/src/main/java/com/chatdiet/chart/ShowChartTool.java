package com.chatdiet.chart;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "show_chart",
        intents = {"show_chart"},
        description = "Render a chart for calories, macros, weight, deficit, or cost over a date range. Compute from/to/granularity yourself from the requested time frame using the current date. includeGoal overlays the calorie target where it applies; cumulative is forced on automatically for hourly (intraday) charts."
)
public class ShowChartTool implements Function<ChartRequest, ToolResult> {

    private final ChartService chartService;
    private final ChartResultContext chartResultContext;

    public ShowChartTool(ChartService chartService, ChartResultContext chartResultContext) {
        this.chartService = chartService;
        this.chartResultContext = chartResultContext;
    }

    @Override
    public ToolResult apply(ChartRequest request) {
        var series = chartService.compute(request);
        chartResultContext.setSeries(series);
        return new ToolResult.Success("Chart rendered inline.", series);
    }
}
