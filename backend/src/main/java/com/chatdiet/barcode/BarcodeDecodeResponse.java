package com.chatdiet.barcode;

/**
 * Response DTO for {@link BarcodeController#decode} and {@link BarcodeController#resolve}.
 *
 * @param upc              the decoded barcode value
 * @param resolvedName     the product's name if identity resolved (cache, Open Food Facts, or FDC
 *                         Branded); {@code null} if {@code needsManualEntry}
 * @param needsManualEntry {@code true} if nothing resolved the barcode - the client should route
 *                         to the manual-entry modal, UPC-prebound, instead of prefilling chat
 * @param wasNew           {@code true} if this call is what added the item to the food_item cache
 *                         (an Open Food Facts or FDC hit); {@code false} for an already-cached hit,
 *                         or when {@code needsManualEntry}
 */
public record BarcodeDecodeResponse(String upc, String resolvedName, boolean needsManualEntry, boolean wasNew) {
}
