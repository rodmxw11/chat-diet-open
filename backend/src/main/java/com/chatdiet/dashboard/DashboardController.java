package com.chatdiet.dashboard;

import com.chatdiet.day.DayBoundaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST surface for the desktop sidebar / mobile chart sheets: macro breakdown and weight trend. */
@RestController
public class DashboardController {

    private final MacroChartService macroChartService;
    private final WeightTrendService weightTrendService;
    private final DayBoundaryService dayBoundaryService;

    public DashboardController(MacroChartService macroChartService, WeightTrendService weightTrendService,
                                DayBoundaryService dayBoundaryService) {
        this.macroChartService = macroChartService;
        this.weightTrendService = weightTrendService;
        this.dayBoundaryService = dayBoundaryService;
    }

    /** Per-day macro breakdown for the last {@code days} (7 or 30) metabolic days, oldest first. */
    @GetMapping("/api/dashboard/macros")
    public List<DailyMacros> macros(@RequestParam(defaultValue = "7") int days) {
        var to = dayBoundaryService.today();
        var from = to.minusDays(Math.max(days, 1) - 1L);
        return macroChartService.dailyMacros(from, to);
    }

    /** The 30-day weight trend: actual weigh-ins, smoothed trend, and goal trajectory. */
    @GetMapping("/api/dashboard/weight-trend")
    public WeightTrendResponse weightTrend() {
        return weightTrendService.trend30Day();
    }
}
