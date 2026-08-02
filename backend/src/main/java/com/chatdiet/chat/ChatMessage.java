package com.chatdiet.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * One row of the durable CHAT_MESSAGE audit log - distinct from ConversationHistoryStore's short
 * in-memory window that's actually replayed to the model.
 *
 * @param role either {@code "user"} or {@code "assistant"}
 */
public record ChatMessage(
        @Id Long id,
        String sessionKey,
        String role,
        String content,
        LocalDateTime createdAt
) {

    @PersistenceCreator
    public ChatMessage {
    }

    /** Creates a new message stamped with the current time. */
    public ChatMessage(String sessionKey, String role, String content) {
        this(null, sessionKey, role, content, LocalDateTime.now());
    }
}
