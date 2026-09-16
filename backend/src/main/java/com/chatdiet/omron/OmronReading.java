package com.chatdiet.omron;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/** Spring Data JDBC entity for a single OMRON blood-pressure-monitor reading. */
public record OmronReading(
        @Id Long id,
        LocalDateTime timestamp,
        Integer systolic,
        Integer diastolic,
        Integer bpm
) {

    @PersistenceCreator
    public OmronReading {
    }
}
