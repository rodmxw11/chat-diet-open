package com.chatdiet.dashboard;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.tdee.AdaptiveTdeeService;
import com.chatdiet.tdee.TdeeResult;
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
    private final AdaptiveTdeeService adaptiveTdeeService;
    private final BloodPressureService bloodPressureService;

    public DashboardController(MacroChartService macroChartService, WeightTrendService weightTrendService,
                                DayBoundaryService dayBoundaryService, AdaptiveTdeeService adaptiveTdeeService,
                                BloodPressureService bloodPressureService) {
        this.macroChartService = macroChartService;
        this.weightTrendService = weightTrendService;
        this.dayBoundaryService = dayBoundaryService;
        this.adaptiveTdeeService = adaptiveTdeeService;
        this.bloodPressureService = bloodPressureService;
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

    /** Blood pressure / heart rate readings for the last {@code days} (7 or 30), oldest first. */
    @GetMapping("/api/dashboard/blood-pressure")
    public List<BloodPressureReading> bloodPressure(@RequestParam(defaultValue = "7") int days) {
        return bloodPressureService.readings(days);
    }

    /** Flat DTO wrapping {@link TdeeResult} so the frontend has one predictable response shape. */
    public record TdeeStatusResponse(Integer estimatedCalories, Integer standardErrorCalories, Integer windowDays,
                                      Integer loggedDays, Double weightChangeLbs, String caveat,
                                      String unavailableReason) {
    }

    /** The adaptive TDEE estimate for the weight trend chart's stat line. */
    @GetMapping("/api/dashboard/tdee")
    public TdeeStatusResponse tdee() {
        return switch (adaptiveTdeeService.estimate()) {
            case TdeeResult.Estimate e -> new TdeeStatusResponse(e.estimatedCalories(), e.standardErrorCalories(),
                    e.windowDays(), e.loggedDays(), e.weightChangeLbs(), e.caveat(), null);
            case TdeeResult.Unavailable u -> new TdeeStatusResponse(null, null, null, null, null, null, u.reason());
        };
    }
}
