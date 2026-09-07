package com.chatdiet.barcode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * REST endpoint for resolving a barcode to a product identity - either decoded server-side from an
 * uploaded product photo ({@link #decode}, the fallback path for browsers without a live in-page
 * scanner), or already decoded client-side and just needing resolution ({@link #resolve}).
 */
@RestController
@RequestMapping("/api/barcode")
public class BarcodeController {

    private final BarcodeDecodeService barcodeDecodeService;
    private final UpcResolutionService upcResolutionService;

    public BarcodeController(BarcodeDecodeService barcodeDecodeService, UpcResolutionService upcResolutionService) {
        this.barcodeDecodeService = barcodeDecodeService;
        this.upcResolutionService = upcResolutionService;
    }

    /**
     * Decodes a barcode from an uploaded image and resolves its product identity server-side, so
     * the client can prefill chat with the resolved name rather than round-tripping the raw UPC
     * digits through a chat turn.
     *
     * @param image the uploaded product photo (multipart form field "image")
     * @return 200 with the decoded UPC and resolution outcome, or 404 if no barcode could be decoded
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
                .map(upc -> {
                    var resolved = upcResolutionService.resolve(upc);
                    return ResponseEntity.ok(
                            new BarcodeDecodeResponse(upc, resolved.resolvedName(), resolved.needsManualEntry()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Resolves a UPC the client already decoded itself (the live in-page scanner's client-side
     * {@code BarcodeDetector}), skipping the image upload and server-side ZXing decode that
     * {@link #decode} does - the value is already in hand, so only identity resolution is needed.
     *
     * @param upc the barcode value read client-side
     * @return 200 with the resolution outcome, same shape as {@link #decode}
     */
    @GetMapping("/resolve")
    public ResponseEntity<BarcodeDecodeResponse> resolve(@RequestParam("upc") String upc) {
        var resolved = upcResolutionService.resolve(upc);
        return ResponseEntity.ok(new BarcodeDecodeResponse(upc, resolved.resolvedName(), resolved.needsManualEntry()));
    }
}
