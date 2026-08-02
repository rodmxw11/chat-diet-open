package com.chatdiet.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Loads the static catalog of {@link IntentDefinition}s from {@code intents/intents.yaml} at
 * startup and exposes the enabled subset that {@link PromptAssembler} builds the system prompt
 * and tool list from.
 */
@Component
public class IntentRegistry {

    private final List<IntentDefinition> intents;

    public IntentRegistry() {
        this.intents = loadIntents();
    }

    /**
     * @throws UncheckedIOException if {@code intents/intents.yaml} is missing or malformed
     */
    private static List<IntentDefinition> loadIntents() {
        var mapper = new YAMLMapper();
        try (var input = new ClassPathResource("intents/intents.yaml").getInputStream()) {
            return mapper.readValue(input, new TypeReference<List<IntentDefinition>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load intents.yaml", e);
        }
    }

    /** Returns the intents whose {@code enabled} flag is true, in YAML declaration order. */
    public List<IntentDefinition> enabledIntents() {
        return intents.stream().filter(IntentDefinition::enabled).toList();
    }
}
