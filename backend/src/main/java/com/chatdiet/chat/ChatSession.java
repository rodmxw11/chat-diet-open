package com.chatdiet.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Durable CHAT_SESSION row tracking one conversation's running size, used to decide when to nudge
 * the user to start a new session. Distinct from the in-memory {@link ConversationHistoryStore},
 * which holds the short window actually replayed to the model.
 *
 * @param endedAt       currently unused/always null - reserved for a future explicit session-close
 * @param tokenEstimate rough running total of chars-per-token estimated tokens across the session
 * @param sizeWarned    true once the one-time "conversation is getting long" notice has been appended
 */
public record ChatSession(
        @Id Long id,
        String sessionKey,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Integer tokenEstimate,
        Boolean sizeWarned
) {

    @PersistenceCreator
    public ChatSession {
    }

    /** Starts a fresh session for the given key with a zero token estimate and no warning yet. */
    public ChatSession(String sessionKey) {
        this(null, sessionKey, LocalDateTime.now(), null, 0, false);
    }

    /** Returns a copy with {@code tokens} added to the running token estimate. */
    public ChatSession withAddedTokens(int tokens) {
        return new ChatSession(id, sessionKey, startedAt, endedAt, (tokenEstimate != null ? tokenEstimate : 0) + tokens,
                sizeWarned);
    }

    /** Returns a copy with the size-warning flag set, so the warning is only ever sent once. */
    public ChatSession warned() {
        return new ChatSession(id, sessionKey, startedAt, endedAt, tokenEstimate, true);
    }
}
