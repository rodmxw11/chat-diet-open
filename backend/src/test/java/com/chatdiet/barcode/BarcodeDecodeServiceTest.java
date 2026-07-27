package com.chatdiet.barcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class BarcodeDecodeServiceTest {

    @Test
    void decodesAGeneratedBarcodeImage() throws Exception {
        var text = "0123456789012";
        var matrix = new MultiFormatWriter().encode(text, BarcodeFormat.CODE_128, 300, 100);
        var image = MatrixToImageWriter.toBufferedImage(matrix);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        var decoded = new BarcodeDecodeService().decode(out.toByteArray());

        assertThat(decoded).contains(text);
    }

    @Test
    void returnsEmptyForAnImageWithNoBarcode() throws Exception {
        var image = new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        var decoded = new BarcodeDecodeService().decode(out.toByteArray());

        assertThat(decoded).isEmpty();
    }
}
