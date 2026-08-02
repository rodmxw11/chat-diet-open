package com.chatdiet.photo;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that analyzes the food photo attached to the current chat message, resizing it
 * for the vision model and returning an estimated description, calories, and macros. If the
 * underlying {@link PhotoAnalysisService} is not confident in the estimate, a clarifying
 * question is returned instead of a final answer.
 */
@Component
@IntentTool(
        name = "analyze_food_photo",
        intents = {"log_food"},
        description = "Analyze a food photo attached to this message. Returns an estimated description, calories, and macros. If confidence is low, a clarifying question is returned instead of a final answer - ask it, and once the user answers, scale the returned numbers arithmetically yourself rather than calling this again."
)
public class AnalyzeFoodPhotoTool implements Function<AnalyzeFoodPhotoRequest, ToolResult> {

    private static final int VISION_MAX_DIMENSION = 1200;

    private final PhotoContext photoContext;
    private final PhotoResizeService photoResizeService;
    private final PhotoAnalysisService photoAnalysisService;

    public AnalyzeFoodPhotoTool(PhotoContext photoContext, PhotoResizeService photoResizeService,
                                 PhotoAnalysisService photoAnalysisService) {
        this.photoContext = photoContext;
        this.photoResizeService = photoResizeService;
        this.photoAnalysisService = photoAnalysisService;
    }

    /**
     * Resizes the request-scoped attached photo and sends it for analysis.
     *
     * @return a {@link ToolResult.NotFound} if no photo is attached, a {@link ToolResult.Success}
     *         with the estimate when confident, or a {@link ToolResult.NeedsClarification}
     *         carrying the analysis question and best-effort numbers when not
     */
    @Override
    public ToolResult apply(AnalyzeFoodPhotoRequest request) {
        var bytes = photoContext.photoBytes();
        if (bytes.isEmpty()) {
            return new ToolResult.NotFound("a photo attached to this message");
        }

        var resized = photoResizeService.resizeToFit(bytes.get(), VISION_MAX_DIMENSION, VISION_MAX_DIMENSION);
        var result = photoAnalysisService.analyze(resized, request.hint());

        if (result.confident()) {
            return new ToolResult.Success(
                    "Photo shows %s: about %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                            .formatted(result.description(), result.calories(), result.proteinG(),
                                    result.carbsG(), result.fatG()),
                    result);
        }

        return new ToolResult.NeedsClarification(
                "%s (estimated so far: %s, about %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat, for the amount shown)"
                        .formatted(result.question(), result.description(), result.calories(),
                                result.proteinG(), result.carbsG(), result.fatG()),
                result);
    }
}
