package com.chatdiet.photo;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Isolated, single-shot subchat on Sonnet - never sees the main conversation, never re-enters
 * context after this one call. Only the computed numbers cross back to the main Haiku loop.
 */
@Service
public class PhotoAnalysisService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a nutrition estimation assistant. You are shown one photo of food and must
            respond with ONLY a single JSON object - no markdown fences, no prose before or after:
            {"items": [{"description": string, "quantity": number, "unit": string}],
             "calories": number, "protein_g": number, "carbs_g": number, "fat_g": number,
             "confidence": "high" or "low", "question": string or null}
            Estimate total nutrition for everything visible. Use "confidence":"low" and a short
            "question" only when a genuinely ambiguous detail (unclear portion count or size)
            would make the estimate meaningless. Otherwise "confidence":"high" and
            "question": null. Always fill calories/protein_g/carbs_g/fat_g with your best
            estimate for what's shown, even when confidence is low.
            """;

    private final ChatClient chatClient;

    public PhotoAnalysisService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder().model(Model.CLAUDE_SONNET_4_5))
                .build();
    }

    public PhotoAnalysisResult analyze(byte[] imageBytes, String hint) {
        var media = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_JPEG)
                .data(new ByteArrayResource(imageBytes))
                .build();

        var userText = (hint == null || hint.isBlank())
                ? "Analyze this food photo."
                : "Analyze this food photo. Context from the user: " + hint;

        var message = UserMessage.builder().text(userText).media(media).build();

        var responseText = chatClient.prompt()
                .messages(message)
                .call()
                .content();

        return parse(responseText);
    }

    private PhotoAnalysisResult parse(String responseText) {
        try {
            var jsonText = stripMarkdownFences(responseText);
            var response = OBJECT_MAPPER.readValue(jsonText, SonnetResponse.class);

            var description = (response.items() == null || response.items().isEmpty())
                    ? "food"
                    : response.items().stream()
                            .map(item -> "%s %s %s".formatted(
                                    item.quantity() != null ? item.quantity() : "",
                                    item.unit() != null ? item.unit() : "",
                                    item.description() != null ? item.description() : "").trim())
                            .collect(Collectors.joining(", "));

            return new PhotoAnalysisResult(
                    description,
                    response.calories() != null ? (int) Math.round(response.calories()) : 0,
                    response.proteinG() != null ? response.proteinG() : 0,
                    response.carbsG() != null ? response.carbsG() : 0,
                    response.fatG() != null ? response.fatG() : 0,
                    !"low".equalsIgnoreCase(response.confidence()),
                    response.question());
        } catch (Exception e) {
            return new PhotoAnalysisResult("unrecognized food", 0, 0, 0, 0, false,
                    "I couldn't read that photo clearly - can you describe what's in it?");
        }
    }

    private static String stripMarkdownFences(String text) {
        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }

    private record SonnetResponse(
            List<Item> items,
            Double calories,
            @JsonProperty("protein_g") Double proteinG,
            @JsonProperty("carbs_g") Double carbsG,
            @JsonProperty("fat_g") Double fatG,
            String confidence,
            String question
    ) {
        record Item(String description, Double quantity, String unit) {
        }
    }
}
