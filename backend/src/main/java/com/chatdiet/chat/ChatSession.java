package com.chatdiet.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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

    public ChatSession(String sessionKey) {
        this(null, sessionKey, LocalDateTime.now(), null, 0, false);
    }

    public ChatSession withAddedTokens(int tokens) {
        return new ChatSession(id, sessionKey, startedAt, endedAt, (tokenEstimate != null ? tokenEstimate : 0) + tokens,
                sizeWarned);
    }

    public ChatSession warned() {
        return new ChatSession(id, sessionKey, startedAt, endedAt, tokenEstimate, true);
    }
}
