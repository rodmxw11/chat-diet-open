package com.chatdiet.barcode;

/**
 * Response DTO for {@link BarcodeController#decode} and {@link BarcodeController#resolve}.
 *
 * @param upc              the decoded barcode value
 * @param resolvedName     the product's name if identity resolved (cache, Open Food Facts, or FDC
 *                         Branded); {@code null} if {@code needsManualEntry}
 * @param needsManualEntry {@code true} if nothing resolved the barcode - the client should route
 *                         to the manual-entry modal, UPC-prebound, instead of the quantity prompt
 * @param wasNew           {@code true} if this call is what added the item to the food_item cache
 *                         (an Open Food Facts or FDC hit); {@code false} for an already-cached hit,
 *                         or when {@code needsManualEntry}
 * @param foodItemId       id of the resolved cached item, for logging by id via
 *                         {@code POST /api/food-entries}; {@code null} if {@code needsManualEntry}
 * @param typicalServingG  the item's typical serving in grams for the quantity prompt's servings
 *                         hint, or {@code null}
 */
public record BarcodeDecodeResponse(String upc, String resolvedName, boolean needsManualEntry, boolean wasNew,
                                     Long foodItemId, Double typicalServingG) {
}