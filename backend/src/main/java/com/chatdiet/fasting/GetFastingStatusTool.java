package com.chatdiet.fasting;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/** IntentTool that reports how long it has been since the last logged food entry. */
@Component
@IntentTool(
        name = "get_fasting_status",
        intents = {"query_data"},
        description = "Get how long it has been since the last logged food entry (the current fasting duration)."
)
public class GetFastingStatusTool implements Function<GetFastingStatusRequest, ToolResult> {

    private final FastingService fastingService;

    public GetFastingStatusTool(FastingService fastingService) {
        this.fastingService = fastingService;
    }

    /**
     * @return a {@link ToolResult.Success} with the current fasting duration, or a
     *         {@link ToolResult.NotFound} if no food has ever been logged
     */
    @Override
    public ToolResult apply(GetFastingStatusRequest request) {
        return fastingService.currentFastDuration()
                .<ToolResult>map(duration -> {
                    long hours = duration.toHours();
                    long minutes = duration.toMinutesPart();
                    return new ToolResult.Success(
                            "It's been %d hr %d min since your last logged food.".formatted(hours, minutes),
                            duration.toString());
                })
                .orElseGet(() -> new ToolResult.NotFound("any logged food to measure fasting from"));
    }
}
