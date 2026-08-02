package com.chatdiet.photo;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Downscales food photos to fit within given dimensions, re-encoding them as JPEG. */
@Service
public class PhotoResizeService {

    /**
     * Scales the image down (never up) so it fits within {@code maxWidth} x {@code maxHeight}
     * while preserving aspect ratio, then re-encodes it as JPEG.
     *
     * @return the resized image as JPEG bytes
     * @throws IllegalArgumentException if {@code original} cannot be read as an image
     * @throws UncheckedIOException     if encoding the resized image fails
     */
    public byte[] resizeToFit(byte[] original, int maxWidth, int maxHeight) {
        try {
            var source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                throw new IllegalArgumentException("Not a readable image");
            }

            double scale = Math.min(
                    (double) maxWidth / source.getWidth(),
                    (double) maxHeight / source.getHeight());
            scale = Math.min(scale, 1.0);

            int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

            var scaled = source.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            var target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            var graphics = target.createGraphics();
            graphics.drawImage(scaled, 0, 0, null);
            graphics.dispose();

            var out = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
