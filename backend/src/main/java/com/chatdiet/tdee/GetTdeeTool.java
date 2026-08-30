package com.chatdiet.tdee;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/** IntentTool that reports the user's back-calculated adaptive TDEE. */
@Component
@IntentTool(
        name = "get_tdee",
        intents = {"query_data"},
        description = "Get the user's adaptive TDEE (estimated total daily energy expenditure), "
                + "back-calculated from their logged calorie intake and smoothed weight trend over "
                + "the last 14 days - not a generic BMR formula."
)
public class GetTdeeTool implements Function<GetTdeeRequest, ToolResult> {

    private final AdaptiveTdeeService adaptiveTdeeService;

    public GetTdeeTool(AdaptiveTdeeService adaptiveTdeeService) {
        this.adaptiveTdeeService = adaptiveTdeeService;
    }

    @Override
    public ToolResult apply(GetTdeeRequest request) {
        return switch (adaptiveTdeeService.estimate()) {
            case TdeeResult.Estimate e -> new ToolResult.Success(
                    ("Your estimated TDEE over the last %d days is about %,d cal/day (based on %d days of food "
                            + "logs and a %s%.1f lb weight-trend change).")
                            .formatted(e.windowDays(), e.estimatedCalories(), e.loggedDays(),
                                    e.weightChangeLbs() >= 0 ? "+" : "", e.weightChangeLbs()),
                    e);
            case TdeeResult.Unavailable u -> new ToolResult.NotFound(u.reason());
        };
    }
}
