package com.chatdiet.digestive;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a logged digestive event (e.g. reflux, diarrhea).
 *
 * @param notes optional freeform notes; may be {@code null} or blank
 */
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
