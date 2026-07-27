package com.chatdiet.vitals;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "correct_vitals_entry",
        intents = {"correct_entry"},
        description = "Correct the value(s) of the most recently logged vitals entry. Use only when the correction is linguistically marked (e.g. \"the correct BP is 156 over 65\"). A bare new reading is a new vitals entry, not a correction."
)
public class CorrectVitalsEntryTool implements Function<CorrectVitalsRequest, ToolResult> {

    private final VitalsEntryRepository vitalsEntryRepository;

    public CorrectVitalsEntryTool(VitalsEntryRepository vitalsEntryRepository) {
        this.vitalsEntryRepository = vitalsEntryRepository;
    }

    @Override
    public ToolResult apply(CorrectVitalsRequest request) {
        var existing = vitalsEntryRepository.findMostRecent();
        if (existing.isEmpty()) {
            return new ToolResult.NotFound("a recent vitals entry");
        }

        var updated = existing.get().corrected(request.systolic(), request.diastolic(), request.heartRate());
        vitalsEntryRepository.save(updated);
        return new ToolResult.Success(
                "Corrected BP %d/%d%s.".formatted(updated.systolic(), updated.diastolic(),
                        updated.heartRate() != null ? ", HR " + updated.heartRate() : ""),
                updated);
    }
}
