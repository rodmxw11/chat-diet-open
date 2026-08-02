package com.chatdiet.sql;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Persisted, reusable SQL query composed by {@link SqlComposerService}. Saving successful
 * compositions lets future questions of the same shape be answered by reusing the exact SQL
 * instead of asking the model to compose it again.
 *
 * @param sqlText       the composed, parameterized SELECT statement, using {@code ?} placeholders
 * @param paramDefsJson the query's {@link ParamDef} list, serialized as JSON (one entry per
 *                      {@code ?} placeholder, in order)
 * @param useCount      number of times this query has been reused, starting at 1 on creation
 * @param lastUsedAt    timestamp of the most recent use (creation or reuse)
 */
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

    /**
     * Returns a copy with the use count incremented and {@code lastUsedAt} refreshed to now,
     * recording that this saved query was reused to answer another question.
     */
    public SavedQuery withUsageBumped() {
        return new SavedQuery(id, name, description, sqlText, paramDefsJson,
                (useCount != null ? useCount : 0) + 1, LocalDateTime.now());
    }
}
