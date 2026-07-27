package com.chatdiet.digestive;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

@Component
@IntentTool(
        name = "log_digestive_event",
        intents = {"log_digestive_event"},
        description = "Log a digestive event such as reflux or diarrhea, with optional freeform notes."
)
public class LogDigestiveEventTool implements Function<LogDigestiveEventRequest, ToolResult> {

    private final DigestiveEventRepository digestiveEventRepository;

    public LogDigestiveEventTool(DigestiveEventRepository digestiveEventRepository) {
        this.digestiveEventRepository = digestiveEventRepository;
    }

    @Override
    public ToolResult apply(LogDigestiveEventRequest request) {
        var entry = new DigestiveEvent(LocalDateTime.now(), request.eventType(), request.notes());
        digestiveEventRepository.save(entry);
        return new ToolResult.Success(
                "Logged: %s%s.".formatted(entry.eventType(),
                        entry.notes() != null && !entry.notes().isBlank() ? " (" + entry.notes() + ")" : ""),
                entry);
    }
}
