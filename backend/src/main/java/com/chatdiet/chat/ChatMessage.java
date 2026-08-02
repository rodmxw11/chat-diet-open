package com.chatdiet.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * The durable CHAT_MESSAGE audit log - distinct from ConversationHistoryStore's short in-memory
 * window that's actually replayed to the model.
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

    public ChatMessage(String sessionKey, String role, String content) {
        this(null, sessionKey, role, content, LocalDateTime.now());
    }
}
