package com.chatdiet.projection;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that projects either the date a goal weight will be reached, or the weight on a
 * future date, based on the current weight trend. Exactly one of {@code goalWeightLbs} or
 * {@code goalDate} should be provided on the request.
 */
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

    /**
     * Dispatches to a goal-date or goal-weight projection depending on which request field is
     * populated.
     *
     * @return a {@link ToolResult.Success} with the projection, a {@link ToolResult.NotFound} if
     *         no projection can be computed (e.g. no weight logged yet), or a
     *         {@link ToolResult.NeedsClarification} if neither field was provided
     */
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
