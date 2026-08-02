package com.chatdiet.note;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/** Spring Data JDBC entity for a freeform timestamped note-to-self, saved via {@link SaveNoteTool}. */
public record Note(@Id Long id, LocalDateTime loggedAt, String text) {

    @PersistenceCreator
    public Note {
    }

    public Note(LocalDateTime loggedAt, String text) {
        this(null, loggedAt, text);
    }
}
