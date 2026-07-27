package com.chatdiet.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class PhotoArchiveService {

    private final Path archiveDir;

    public PhotoArchiveService(@Value("${chat-diet.photo-archive-dir:./data/photos}") String archiveDir) {
        this.archiveDir = Path.of(archiveDir);
    }

    public String archive(byte[] jpegBytes) {
        try {
            Files.createDirectories(archiveDir);
            var fileName = UUID.randomUUID() + ".jpg";
            var path = archiveDir.resolve(fileName);
            Files.write(path, jpegBytes);
            return path.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String archivePath) {
        try {
            Files.deleteIfExists(Path.of(archivePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
