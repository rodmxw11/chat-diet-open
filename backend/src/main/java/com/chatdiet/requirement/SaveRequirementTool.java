package com.chatdiet.requirement;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * IntentTool that records a feature request or complaint about the app itself (as opposed to
 * diet/weight/vitals data) to the feature backlog.
 */
@Component
@IntentTool(
        name = "save_requirement",
        intents = {"capture_requirement"},
        description = "Record a feature request or complaint about the app itself, not about diet/weight/vitals data."
)
public class SaveRequirementTool implements Function<SaveRequirementRequest, ToolResult> {

    private final RequirementEntryRepository requirementEntryRepository;

    public SaveRequirementTool(RequirementEntryRepository requirementEntryRepository) {
        this.requirementEntryRepository = requirementEntryRepository;
    }

    /** Saves a new {@link RequirementEntry} with status {@code "OPEN"}. */
    @Override
    public ToolResult apply(SaveRequirementRequest request) {
        var entry = new RequirementEntry(LocalDateTime.now(), request.rawText(), request.summary());
        requirementEntryRepository.save(entry);
        return new ToolResult.Success("Got it - added to the feature backlog: " + entry.summary(), entry);
    }
}
