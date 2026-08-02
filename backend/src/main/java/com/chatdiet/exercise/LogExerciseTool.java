package com.chatdiet.exercise;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

/** IntentTool that logs an exercise session by name and duration, with an optional calorie burn estimate. */
@Component
@IntentTool(
        name = "log_exercise",
        intents = {"log_exercise"},
        description = "Log an exercise session by name and duration, with an optional calorie burn estimate."
)
public class LogExerciseTool implements Function<LogExerciseRequest, ToolResult> {

    private final ExerciseEntryRepository exerciseEntryRepository;

    public LogExerciseTool(ExerciseEntryRepository exerciseEntryRepository) {
        this.exerciseEntryRepository = exerciseEntryRepository;
    }

    /** Saves a new timestamped {@link ExerciseEntry}. */
    @Override
    public ToolResult apply(LogExerciseRequest request) {
        var entry = new ExerciseEntry(LocalDateTime.now(), request.exerciseName(),
                request.durationMinutes(), request.caloriesBurned());
        exerciseEntryRepository.save(entry);
        return new ToolResult.Success(
                "Logged exercise: %s, %d min%s.".formatted(entry.exerciseName(), entry.durationMinutes(),
                        entry.caloriesBurned() != null ? ", " + entry.caloriesBurned() + " kcal burned" : ""),
                entry);
    }
}
