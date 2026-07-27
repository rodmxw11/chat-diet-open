package com.chatdiet.projection;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "get_weight_projection",
        intents = {"query_data"},
        description = "Project either the date a goal weight will be reached, or the weight on a future date, based on the current weight trend. Provide exactly one of goalWeightLbs or goalDate."
)
public class GetWeightProjectionTool implements Function<GetWeightProjectionRequest, ToolResult> {

    private final ProjectionService projectionService;

    public GetWeightProjectionTool(ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public ToolResult apply(GetWeightProjectionRequest request) {
        if (request.goalWeightLbs() != null && request.goalWeightLbs() > 0) {
            return projectionService.projectGoalDate(request.goalWeightLbs())
                    .<ToolResult>map(date -> new ToolResult.Success(
                            "Projected to reach %.1f lbs around %s at the current rate."
                                    .formatted(request.goalWeightLbs(), date),
                            date))
                    .orElseGet(() -> new ToolResult.NotFound("a projection - log a weight first, or the goal doesn't match the current trend direction"));
        }

        var goalDate = request.parsedGoalDate();
        if (goalDate != null) {
            return projectionService.projectWeightOn(goalDate)
                    .<ToolResult>map(weight -> new ToolResult.Success(
                            "Projected weight on %s: %.1f lbs at the current rate.".formatted(goalDate, weight),
                            weight))
                    .orElseGet(() -> new ToolResult.NotFound("a projection - log a weight first"));
        }

        return new ToolResult.NeedsClarification("Do you want a projected date for a goal weight, or a projected weight on a given date?", null);
    }
}
