package com.chatdiet.barcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Decodes at full resolution, no downscaling - barcode density doesn't survive
 * the resizing used for vision-analysis photos.
 */
@Service
public class BarcodeDecodeService {

    public Optional<String> decode(byte[] imageBytes) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return Optional.empty();
            }
            var source = new BufferedImageLuminanceSource(image);
            var bitmap = new BinaryBitmap(new HybridBinarizer(source));
            var result = new MultiFormatReader().decode(bitmap);
            return Optional.of(result.getText());
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
