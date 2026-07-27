package com.chatdiet.intent;

import java.util.List;

public record IntentDefinition(
        String name,
        String description,
        List<String> toolNames,
        String promptFragment,
        boolean enabled
) {
}
