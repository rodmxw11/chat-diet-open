package com.chatdiet.photo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PhotoPurgeJobTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasourceAndArchiveDir(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("purge-test"));
        registry.add("chat-diet.photo-archive-dir", () -> tempDir.resolve("photos").toString());
    }

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private PhotoArchiveService photoArchiveService;

    @Autowired
    private PhotoPurgeJob photoPurgeJob;

    @Test
    void deletesExpiredPhotosAndTheirFilesButKeepsUnexpiredOnes() throws Exception {
        var archivePath = photoArchiveService.archive(new byte[]{1, 2, 3});
        var expired = photoRepository.save(new Photo(1L, archivePath, LocalDateTime.now().minusDays(91),
                LocalDateTime.now().minusDays(1)));

        var keptArchivePath = photoArchiveService.archive(new byte[]{4, 5, 6});
        var kept = photoRepository.save(new Photo(2L, keptArchivePath, LocalDateTime.now(),
                LocalDateTime.now().plusDays(89)));

        var purgedCount = photoPurgeJob.run();

        assertThat(purgedCount).isEqualTo(1);
        assertThat(photoRepository.findById(expired.id())).isEmpty();
        assertThat(Files.exists(Path.of(archivePath))).isFalse();
        assertThat(photoRepository.findById(kept.id())).isPresent();
        assertThat(Files.exists(Path.of(keptArchivePath))).isTrue();
    }
}
