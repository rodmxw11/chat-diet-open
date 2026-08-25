package com.chatdiet.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the durable CHAT_MESSAGE log. Messages are grouped by the metabolic day they were
 * composed on - there is no separate session concept - so a day's whole conversation can be
 * replayed to the model or handed back to the UI.
 *
 * @param metabolicDate         the metabolic day this message belongs to, always derivable from
 *                              {@code createdAt} via {@link com.chatdiet.day.DayBoundaryService},
 *                              but stored denormalized so it can be grouped by directly
 * @param role                  {@code "user"} or {@code "assistant"}
 * @param createdAt             when the message was composed, which for an offline-queued message
 *                              is earlier than when the server received it
 * @param promptTokens          main chat model's prompt tokens for this turn, or {@code null} for
 *                              the user half of the turn (usage is reported per model call, which
 *                              covers the whole turn, so it's only ever recorded on the assistant row)
 * @param completionTokens      main chat model's completion tokens for this turn, or {@code null}
 * @param totalTokens           main chat model's total tokens for this turn, or {@code null}
 * @param opusPromptTokens      the isolated SQL-composer subchat's prompt tokens, summed across
 *                              every {@code run_sql} invocation in this turn, or {@code null} if
 *                              {@code run_sql} wasn't invoked
 * @param opusCompletionTokens  the SQL-composer subchat's completion tokens, or {@code null}
 * @param opusTotalTokens       the SQL-composer subchat's total tokens, or {@code null}
 */
public record ChatMessage(
        @Id Long id,
        LocalDate metabolicDate,
        String role,
        String content,
        LocalDateTime createdAt,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer opusPromptTokens,
        Integer opusCompletionTokens,
        Integer opusTotalTokens
) {

    @PersistenceCreator
    public ChatMessage {
    }

    /** Creates a new, unpersisted message with no token usage recorded (e.g. the user half of a turn). */
    public ChatMessage(LocalDate metabolicDate, String role, String content, LocalDateTime createdAt) {
        this(null, metabolicDate, role, content, createdAt, null, null, null, null, null, null);
    }

    /** Creates a new, unpersisted message with token usage recorded (the assistant half of a turn). */
    public ChatMessage(LocalDate metabolicDate, String role, String content, LocalDateTime createdAt,
                        TokenUsage chatUsage, TokenUsage opusUsage) {
        this(null, metabolicDate, role, content, createdAt,
                chatUsage != null ? chatUsage.promptTokens() : null,
                chatUsage != null ? chatUsage.completionTokens() : null,
                chatUsage != null ? chatUsage.totalTokens() : null,
                opusUsage != null ? opusUsage.promptTokens() : null,
                opusUsage != null ? opusUsage.completionTokens() : null,
                opusUsage != null ? opusUsage.totalTokens() : null);
    }
}
