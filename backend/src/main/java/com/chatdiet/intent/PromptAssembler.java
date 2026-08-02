package com.chatdiet.intent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Builds the single system prompt and tool list used to configure the app's one {@code ChatClient}.
 * Combines a fixed base persona with the prompt fragments of every enabled {@link IntentDefinition}
 * (from {@link IntentRegistry}), stamps in the current date/time and metabolic-day rollover hour,
 * and resolves each enabled intent's tool names to actual {@link org.springframework.ai.tool.ToolCallback}s
 * via {@link ToolRegistry}. Called once at {@code ChatService} startup.
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
     * Assembles the full system prompt (base persona + current date/time + each enabled intent's
     * prompt fragment) and the deduplicated list of tool callbacks for all enabled intents' tool
     * names.
     */
    public AssembledPrompt assemble() {
        var enabledIntents = intentRegistry.enabledIntents();

        var fragments = enabledIntents.stream()
                .map(IntentDefinition::promptFragment)
                .collect(Collectors.joining("\n"));

        var systemPrompt = BASE_PERSONA
                + "\nCurrent date/time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + ". The metabolic day rolls over at " + dayRolloverHour + ":00, not calendar midnight.\n\n"
                + fragments;

        var toolNames = enabledIntents.stream()
                .flatMap(intent -> intent.toolNames().stream())
                .distinct()
                .toList();

        return new AssembledPrompt(systemPrompt, toolRegistry.toolsFor(toolNames));
    }
}
