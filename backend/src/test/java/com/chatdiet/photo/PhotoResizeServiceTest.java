package com.chatdiet.photo;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoResizeServiceTest {

    private final PhotoResizeService service = new PhotoResizeService();

    @Test
    void scalesDownPreservingAspectRatioToFitTheBoundingBox() throws Exception {
        var original = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(original, "jpg", out);

        var resized = service.resizeToFit(out.toByteArray(), 1200, 1200);
        var image = ImageIO.read(new ByteArrayInputStream(resized));

        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(900);
    }

    @Test
    void neverUpscalesAnImageSmallerThanTheBoundingBox() throws Exception {
        var original = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(original, "jpg", out);

        var resized = service.resizeToFit(out.toByteArray(), 320, 480);
        var image = ImageIO.read(new ByteArrayInputStream(resized));

        assertThat(image.getWidth()).isEqualTo(100);
        assertThat(image.getHeight()).isEqualTo(80);
    }
}
