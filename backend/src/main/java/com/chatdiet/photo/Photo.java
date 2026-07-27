package com.chatdiet.photo;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

public record Photo(
        @Id Long id,
        Long foodEntryId,
        String archivePath,
        LocalDateTime capturedAt,
        LocalDateTime purgeAfter
) {

    @PersistenceCreator
    public Photo {
    }

    public Photo(Long foodEntryId, String archivePath, LocalDateTime capturedAt, LocalDateTime purgeAfter) {
        this(null, foodEntryId, archivePath, capturedAt, purgeAfter);
    }
}
