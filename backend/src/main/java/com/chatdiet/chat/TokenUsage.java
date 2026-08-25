package com.chatdiet.chat;

import org.springframework.ai.chat.metadata.Usage;

/**
 * Token usage from one model call, as reported by the provider. Persisted alongside each
 * assistant {@link ChatMessage} for cost reporting - no cost math is done here on purpose; that's
 * a reporting-time concern (pricing changes, and varies by which model produced the tokens).
 */
public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    public static TokenUsage from(Usage usage) {
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /** Sums two usages, e.g. when a single chat turn triggers more than one SQL-composer call. */
    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                sum(promptTokens, other.promptTokens),
                sum(completionTokens, other.completionTokens),
                sum(totalTokens, other.totalTokens));
    }

    private static Integer sum(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }
}
