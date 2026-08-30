package com.chatdiet.chat;

import org.springframework.data.annotation.Id;

/**
 * USD cost per million tokens for one model, used to turn a day's recorded token usage into an
 * approximate dollar figure on the chat history page. A flat snapshot, not a history - if
 * Anthropic's published rates change, update the row directly rather than versioning it.
 */
public record ModelPricing(
        @Id Long id,
        String model,
        Double inputCostPerMillionUsd,
        Double outputCostPerMillionUsd
) {
}
