package com.chatdiet.barcode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/barcode")
public class BarcodeController {

    private final BarcodeDecodeService barcodeDecodeService;

    public BarcodeController(BarcodeDecodeService barcodeDecodeService) {
        this.barcodeDecodeService = barcodeDecodeService;
    }

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
