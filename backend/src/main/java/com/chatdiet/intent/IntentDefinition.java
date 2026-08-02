package com.chatdiet.intent;

import java.util.List;

/**
 * One entry of {@code intents/intents.yaml}, deserialized by {@link IntentRegistry}. An intent
 * groups a chunk of system-prompt text ({@code promptFragment}) with the set of tools (by the
 * name each tool declares via {@link IntentTool#name()}) the model should have available in
 * order to act on that intent.
 *
 * @param name           the intent's identifier, matched against {@link IntentTool#intents()}
 * @param description    human-readable description; not shown to the model
 * @param toolNames      names of {@link IntentTool}-annotated beans to expose when this intent is enabled
 * @param promptFragment text appended to the base persona in the assembled system prompt
 * @param enabled        whether this intent (and its tools/prompt fragment) is active
 */
public record IntentDefinition(
        String name,
        String description,
        List<String> toolNames,
        String promptFragment,
        boolean enabled
) {
}
