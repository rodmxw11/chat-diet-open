package com.chatdiet.barcode;

/**
 * Response DTO for {@link BarcodeController#decode}.
 *
 * @param upc              the decoded barcode value
 * @param resolvedName     the product's name if identity resolved (cache, Open Food Facts, or FDC
 *                         Branded); {@code null} if {@code needsManualEntry}
 * @param needsManualEntry {@code true} if nothing resolved the barcode - the client should route
 *                         to the manual-entry modal, UPC-prebound, instead of prefilling chat
 */
public record BarcodeDecodeResponse(String upc, String resolvedName, boolean needsManualEntry) {
}
