package com.chatdiet.photo;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for an archived photo of a logged food entry.
 *
 * @param foodEntryId the {@link com.chatdiet.food.FoodEntry} this photo was captured for
 * @param archivePath filesystem path where the resized JPEG copy was written by
 *                    {@link PhotoArchiveService}
 * @param purgeAfter  timestamp after which {@link PhotoPurgeJob} permanently deletes this photo
 */
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
