package com.chatdiet.weight;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "correct_weight_entry",
        intents = {"correct_entry"},
        description = "Correct the value of the most recently logged weight entry. Use only when the correction is linguistically marked (e.g. \"the scale actually said 181\"). A bare new number is a new weight entry, not a correction."
)
public class CorrectWeightEntryTool implements Function<CorrectWeightRequest, ToolResult> {

    private final WeightEntryRepository weightEntryRepository;

    public CorrectWeightEntryTool(WeightEntryRepository weightEntryRepository) {
        this.weightEntryRepository = weightEntryRepository;
    }

    @Override
    public ToolResult apply(CorrectWeightRequest request) {
        var existing = weightEntryRepository.findMostRecent();
        if (existing.isEmpty()) {
            return new ToolResult.NotFound("a recent weight entry");
        }

        var updated = existing.get().corrected(request.weightLbs());
        weightEntryRepository.save(updated);
        return new ToolResult.Success("Corrected weight: %.1f lbs.".formatted(updated.weightLbs()), updated);
    }
}
