package com.chatdiet.requirement;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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
