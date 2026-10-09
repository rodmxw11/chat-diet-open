package com.chatdiet.measurement;

import org.springframework.ai.tool.annotation.ToolParam;

/** Request for the {@code log_measurements} tool - any subset of waist, neck and hip, in inches. */
public record LogMeasurementsRequest(
        @ToolParam(required = false, description = "Waist circumference in inches (convert cm by dividing by 2.54).")
        Double waistIn,
        @ToolParam(required = false, description = "Neck circumference in inches.")
        Double neckIn,
        @ToolParam(required = false, description = "Hip circumference in inches.")
        Double hipIn,
        @ToolParam(required = false, description = "ISO-8601 local time measured, e.g. 2026-10-09T07:30; "
                + "a date alone logs at noon. Omit to log under now.")
        String measuredAt
) {
}
