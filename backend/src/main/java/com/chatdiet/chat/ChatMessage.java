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
 * @param metabolicDate the metabolic day this message belongs to, always derivable from
 *                      {@code createdAt} via {@link com.chatdiet.day.DayBoundaryService}, but
 *                      stored denormalized so it can be grouped by directly
 * @param role          {@code "user"} or {@code "assistant"}
 * @param createdAt     when the message was composed, which for an offline-queued message is
 *                      earlier than when the server received it
 */
public record ChatMessage(
        @Id Long id,
        LocalDate metabolicDate,
        String role,
        String content,
        LocalDateTime createdAt
) {

    @PersistenceCreator
    public ChatMessage {
    }

    /** Creates a new, unpersisted message. */
    public ChatMessage(LocalDate metabolicDate, String role, String content, LocalDateTime createdAt) {
        this(null, metabolicDate, role, content, createdAt);
    }
}
