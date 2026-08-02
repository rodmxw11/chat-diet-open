package com.chatdiet.vitals;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that corrects the value(s) of the most recently logged vitals entry, in place,
 * rather than creating a new entry. Intended for linguistically marked corrections (e.g. "the
 * correct BP is 156 over 65"); a bare new reading should instead be logged via
 * {@link LogVitalsTool}.
 */
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

    /**
     * Applies the correction to the most recently logged vitals entry, leaving any field not
     * present in the request unchanged.
     *
     * @return a {@link ToolResult.NotFound} if there is no vitals entry to correct, otherwise a
     *         {@link ToolResult.Success} wrapping the updated {@link VitalsEntry}
     */
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
