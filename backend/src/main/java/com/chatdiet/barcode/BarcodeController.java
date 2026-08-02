package com.chatdiet.barcode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** REST endpoint for decoding a barcode from an uploaded product photo. */
@RestController
@RequestMapping("/api/barcode")
public class BarcodeController {

    private final BarcodeDecodeService barcodeDecodeService;

    public BarcodeController(BarcodeDecodeService barcodeDecodeService) {
        this.barcodeDecodeService = barcodeDecodeService;
    }

    /**
     * Decodes a barcode from an uploaded image.
     *
     * @param image the uploaded product photo (multipart form field "image")
     * @return 200 with the decoded UPC, or 404 if no barcode could be decoded
     * @throws UncheckedIOException if the uploaded file cannot be read
     */
    @PostMapping(value = "/decode", consumes = "multipart/form-data")
    public ResponseEntity<BarcodeDecodeResponse> decode(@RequestParam("image") MultipartFile image) {
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return barcodeDecodeService.decode(bytes)
                .map(upc -> ResponseEntity.ok(new BarcodeDecodeResponse(upc)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
