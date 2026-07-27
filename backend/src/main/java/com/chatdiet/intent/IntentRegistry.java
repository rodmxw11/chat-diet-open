package com.chatdiet.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Component
public class IntentRegistry {

    private final List<IntentDefinition> intents;

    public IntentRegistry() {
        this.intents = loadIntents();
    }

    private static List<IntentDefinition> loadIntents() {
        var mapper = new YAMLMapper();
        try (var input = new ClassPathResource("intents/intents.yaml").getInputStream()) {
            return mapper.readValue(input, new TypeReference<List<IntentDefinition>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load intents.yaml", e);
        }
    }

    public List<IntentDefinition> enabledIntents() {
        return intents.stream().filter(IntentDefinition::enabled).toList();
    }
}
