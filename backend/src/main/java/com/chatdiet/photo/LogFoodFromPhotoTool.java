package com.chatdiet.photo;

import com.chatdiet.food.FoodEntry;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

@Component
@IntentTool(
        name = "log_food_from_photo",
        intents = {"log_food"},
        description = "Log a food entry from the photo attached to this message, using the final calories/macros (after any clarification and arithmetic). Archives a small copy of the photo. Skip calling this for anything under 10 calories - just acknowledge instead."
)
public class LogFoodFromPhotoTool implements Function<LogFoodFromPhotoRequest, ToolResult> {

    private static final int ARCHIVE_MAX_WIDTH = 320;
    private static final int ARCHIVE_MAX_HEIGHT = 480;
    private static final int PURGE_AFTER_DAYS = 90;

    private final PhotoContext photoContext;
    private final PhotoResizeService photoResizeService;
    private final PhotoArchiveService photoArchiveService;
    private final FoodEntryRepository foodEntryRepository;
    private final PhotoRepository photoRepository;

    public LogFoodFromPhotoTool(PhotoContext photoContext, PhotoResizeService photoResizeService,
                                 PhotoArchiveService photoArchiveService, FoodEntryRepository foodEntryRepository,
                                 PhotoRepository photoRepository) {
        this.photoContext = photoContext;
        this.photoResizeService = photoResizeService;
        this.photoArchiveService = photoArchiveService;
        this.foodEntryRepository = foodEntryRepository;
        this.photoRepository = photoRepository;
    }

    @Override
    public ToolResult apply(LogFoodFromPhotoRequest request) {
        var bytes = photoContext.photoBytes();
        if (bytes.isEmpty()) {
            return new ToolResult.NotFound("a photo attached to this message");
        }

        if (request.totalCalories() < 10) {
            return new ToolResult.Success(
                    "Noted \"" + request.description() + "\" - under 10 calories, not logged.", null);
        }

        var now = LocalDateTime.now();
        var entry = new FoodEntry(now, request.description(), request.totalCalories(),
                request.totalProteinG(), request.totalCarbsG(), request.totalFatG(), "PHOTO_ESTIMATE");
        entry = foodEntryRepository.save(entry);

        var archived = photoResizeService.resizeToFit(bytes.get(), ARCHIVE_MAX_WIDTH, ARCHIVE_MAX_HEIGHT);
        var archivePath = photoArchiveService.archive(archived);
        photoRepository.save(new Photo(entry.id(), archivePath, now, now.plusDays(PURGE_AFTER_DAYS)));

        return new ToolResult.Success(
                "Logged \"%s\": %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(entry.rawUtterance(), entry.totalCalories(), entry.totalProteinG(),
                                entry.totalCarbsG(), entry.totalFatG()),
                entry);
    }
}
