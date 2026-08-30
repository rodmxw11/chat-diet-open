package com.chatdiet.chat;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

/**
 * Turns a metabolic day's {@link ChatMessage} rows into an approximate USD cost per model, using
 * the flat per-million-token rates in {@link ModelPricingRepository}. Rounded to the cent - this
 * is an at-a-glance figure for the chat history page, not a billing reconciliation.
 */
@Component
public class ChatCostCalculator {

    private final ModelPricingRepository modelPricingRepository;

    public ChatCostCalculator(ModelPricingRepository modelPricingRepository) {
        this.modelPricingRepository = modelPricingRepository;
    }

    public record DailyCost(double haikuCostUsd, double opusCostUsd) {
    }

    public DailyCost costFor(List<ChatMessage> messages) {
        var haikuCost = costForModel("HAIKU",
                sumTokens(messages, ChatMessage::promptTokens), sumTokens(messages, ChatMessage::completionTokens));
        var opusCost = costForModel("OPUS",
                sumTokens(messages, ChatMessage::opusPromptTokens), sumTokens(messages, ChatMessage::opusCompletionTokens));
        return new DailyCost(haikuCost, opusCost);
    }

    private double costForModel(String model, long promptTokens, long completionTokens) {
        return modelPricingRepository.findByModel(model)
                .map(pricing -> roundToCents(promptTokens, completionTokens,
                        pricing.inputCostPerMillionUsd(), pricing.outputCostPerMillionUsd()))
                .orElse(0.0);
    }

    /**
     * Pure cost math - split out from the repository lookup so it's directly testable without a
     * Spring context.
     */
    static double roundToCents(long promptTokens, long completionTokens,
                                double inputCostPerMillionUsd, double outputCostPerMillionUsd) {
        var raw = promptTokens * inputCostPerMillionUsd / 1_000_000.0
                + completionTokens * outputCostPerMillionUsd / 1_000_000.0;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static long sumTokens(List<ChatMessage> messages, Function<ChatMessage, Integer> field) {
        return messages.stream()
                .mapToLong(message -> {
                    Integer value = field.apply(message);
                    return value != null ? value : 0;
                })
                .sum();
    }
}
