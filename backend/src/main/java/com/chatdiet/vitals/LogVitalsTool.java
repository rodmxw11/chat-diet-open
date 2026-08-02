package com.chatdiet.vitals;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

/** IntentTool that logs a new blood pressure reading, optionally with heart rate. */
@Component
@IntentTool(
        name = "log_vitals",
        intents = {"log_vitals"},
        description = "Log a blood pressure reading (systolic/diastolic), optionally with heart rate."
)
public class LogVitalsTool implements Function<LogVitalsRequest, ToolResult> {

    private final VitalsEntryRepository vitalsEntryRepository;

    public LogVitalsTool(VitalsEntryRepository vitalsEntryRepository) {
        this.vitalsEntryRepository = vitalsEntryRepository;
    }

    /**
     * Saves a new timestamped {@link VitalsEntry} from the request.
     *
     * @return a {@link ToolResult.Success} wrapping the newly created entry
     */
    @Override
    public ToolResult apply(LogVitalsRequest request) {
        var entry = new VitalsEntry(LocalDateTime.now(), request.systolic(), request.diastolic(), request.heartRate());
        vitalsEntryRepository.save(entry);
        return new ToolResult.Success(
                "Logged BP %d/%d%s.".formatted(entry.systolic(), entry.diastolic(),
                        entry.heartRate() != null ? ", HR " + entry.heartRate() : ""),
                entry);
    }
}
