package com.chatdiet.weight;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

/** IntentTool implementation backing {@code log_weight}: persists a new weight reading timestamped now. */
@Component
@IntentTool(
        name = "log_weight",
        intents = {"log_weight"},
        description = "Log a body weight reading in pounds."
)
public class LogWeightTool implements Function<LogWeightRequest, ToolResult> {

    private final WeightEntryRepository weightEntryRepository;

    public LogWeightTool(WeightEntryRepository weightEntryRepository) {
        this.weightEntryRepository = weightEntryRepository;
    }

    @Override
    public ToolResult apply(LogWeightRequest request) {
        var entry = new WeightEntry(LocalDateTime.now(), request.weightLbs());
        weightEntryRepository.save(entry);
        return new ToolResult.Success("Logged weight: %.1f lbs.".formatted(entry.weightLbs()), entry);
    }
}
