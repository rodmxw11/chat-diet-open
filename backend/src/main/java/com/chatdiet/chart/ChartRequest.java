package com.chatdiet.chart;

import java.time.LocalDate;

/**
 * Parameters for {@code show_chart}, provided by the model. {@code from}/{@code to} and the
 * granularity are computed by the model itself from the requested time frame, not parsed here.
 *
 * @param includeGoal overlays the calorie target series where applicable (CALORIES/DEFICIT)
 * @param cumulative  running-total instead of per-bucket values; forced on automatically by
 *                     {@link ChartService} for {@link Granularity#HOUR} charts regardless of this flag
 */
public record ChartRequest(
        ChartMetric metric,
        LocalDate from,
        LocalDate to,
        Granularity granularity,
        boolean includeGoal,
        boolean cumulative
) {
}
