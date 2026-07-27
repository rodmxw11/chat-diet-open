package com.chatdiet.photo;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

/** Holds the raw bytes of a photo attached to the current chat HTTP request, if any. */
@Component
@RequestScope
public class PhotoContext {

    private byte[] photoBytes;

    public void setPhotoBytes(byte[] photoBytes) {
        this.photoBytes = photoBytes;
    }

    public Optional<byte[]> photoBytes() {
        return Optional.ofNullable(photoBytes);
    }
}
