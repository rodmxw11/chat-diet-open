package com.chatdiet.photo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Hard-deletes photos past 90 days - permanent, no archive tier. */
@Component
public class PhotoPurgeJob {

    private final PhotoRepository photoRepository;
    private final PhotoArchiveService photoArchiveService;

    public PhotoPurgeJob(PhotoRepository photoRepository, PhotoArchiveService photoArchiveService) {
        this.photoRepository = photoRepository;
        this.photoArchiveService = photoArchiveService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredPhotos() {
        run();
    }

    public int run() {
        var due = photoRepository.findDueForPurge(LocalDateTime.now());
        for (var photo : due) {
            photoArchiveService.delete(photo.archivePath());
            photoRepository.deleteById(photo.id());
        }
        return due.size();
    }
}
