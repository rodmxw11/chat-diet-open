package com.chatdiet.requirement;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a feature request or complaint about the app itself, captured via
 * {@link SaveRequirementTool}.
 *
 * @param rawText the user's original wording
 * @param summary a short model-generated summary of the request
 * @param status  free-text workflow state; new entries are created with {@code "OPEN"}
 */
public record RequirementEntry(
        @Id Long id,
        LocalDateTime loggedAt,
        String rawText,
        String summary,
        String status
) {

    @PersistenceCreator
    public RequirementEntry {
    }

    public RequirementEntry(LocalDateTime loggedAt, String rawText, String summary) {
        this(null, loggedAt, rawText, summary, "OPEN");
    }
}
