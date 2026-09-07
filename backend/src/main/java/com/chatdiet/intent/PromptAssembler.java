package com.chatdiet.intent;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the system prompt and tool list for the app's one {@code ChatClient}. Combines a fixed
 * base persona with the prompt fragments of every enabled {@link IntentDefinition} (from
 * {@link IntentRegistry}), stamps in the current date/time and metabolic-day rollover hour, and
 * resolves each enabled intent's tool names to actual {@link ToolCallback}s via {@link ToolRegistry}.
 *
 * <p>{@link #systemPrompt()} must be called fresh on every chat turn, not cached: it stamps in
 * {@code LocalDateTime.now()}, and the model resolves "today"/"yesterday"/loggedAt timestamps
 * against whatever that string says. A version baked in once at {@code ChatService} construction
 * freezes the model's notion of "now" at server-boot time for as long as the process keeps
 * running - which silently mis-dates (or, if the model dead-reckons a plausible-but-wrong
 * loggedAt instead of leaving it unset, mis-times) anything logged after that. {@link #tools()} is
 * fine to call once at startup - the intent-to-tool mapping doesn't change at runtime.
 */
@Component
public class PromptAssembler {

    private static final String BASE_PERSONA = """
            You are the backend of a personal diet, weight, and vitals tracking
            app. Chat is the entire interface. Follow these principles on every
            response:
            1. You are not a nanny. Never volunteer commentary on data - not
               out-of-range vitals, not calorie progress, not food choices.
               Answer what is asked and log what you are told.
            2. Echo every number you persist, so transcription errors are
               visible immediately.
            3. Save immediately, correct after. Do not ask for confirmation
               before writing, except when input is genuinely ambiguous and a
               missing value would make the estimate meaningless.
            4. Corrections must be linguistically marked (e.g. "the correct BP
               is 156 over 65", "make that two slices"). A bare number is
               always a new entry, never an inferred correction.
            5. Sub-10-calorie items (black tea, water) are not logged - a brief
               acknowledgement is enough, no row written.
            6. When estimating calories or macros yourself (nothing cached or
               found in a database), err a few percent high, never low - the
               user prefers mild overestimation; underestimation quietly
               undermines their weight-loss goal.
            """;

    private final IntentRegistry intentRegistry;
    private final ToolRegistry toolRegistry;

    @Value("${chat-diet.day-rollover-hour:4}")
    private int dayRolloverHour;

    public PromptAssembler(IntentRegistry intentRegistry, ToolRegistry toolRegistry) {
        this.intentRegistry = intentRegistry;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Builds the full system prompt (base persona + current date/time + each enabled intent's
     * prompt fragment), freshly on every call so the stamped date/time is never stale.
     */
    public String systemPrompt() {
        var fragments = intentRegistry.enabledIntents().stream()
                .map(IntentDefinition::promptFragment)
                .collect(Collectors.joining("\n"));

        return BASE_PERSONA
                + "\nCurrent date/time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + ". The metabolic day rolls over at " + dayRolloverHour + ":00, not calendar midnight.\n\n"
                + fragments;
    }

    /**
     * Resolves the deduplicated list of tool callbacks for every enabled intent's tool names. Safe
     * to call once at startup - unlike {@link #systemPrompt()}, nothing here depends on the current
     * time.
     */
    public List<ToolCallback> tools() {
        var toolNames = intentRegistry.enabledIntents().stream()
                .flatMap(intent -> intent.toolNames().stream())
                .distinct()
                .toList();
        return toolRegistry.toolsFor(toolNames);
    }
}
