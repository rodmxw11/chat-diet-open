package com.chatdiet.sql;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

public record SavedQuery(
        @Id Long id,
        String name,
        String description,
        String sqlText,
        String paramDefsJson,
        Integer useCount,
        LocalDateTime lastUsedAt
) {

    @PersistenceCreator
    public SavedQuery {
    }

    public SavedQuery(String name, String description, String sqlText, String paramDefsJson) {
        this(null, name, description, sqlText, paramDefsJson, 1, LocalDateTime.now());
    }

    public SavedQuery withUsageBumped() {
        return new SavedQuery(id, name, description, sqlText, paramDefsJson,
                (useCount != null ? useCount : 0) + 1, LocalDateTime.now());
    }
}
