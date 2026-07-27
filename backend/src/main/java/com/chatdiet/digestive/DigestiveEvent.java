package com.chatdiet.digestive;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

public record DigestiveEvent(
        @Id Long id,
        LocalDateTime loggedAt,
        String eventType,
        String notes
) {

    @PersistenceCreator
    public DigestiveEvent {
    }

    public DigestiveEvent(LocalDateTime loggedAt, String eventType, String notes) {
        this(null, loggedAt, eventType, notes);
    }
}
