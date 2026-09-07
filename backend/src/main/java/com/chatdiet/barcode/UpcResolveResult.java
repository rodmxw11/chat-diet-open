package com.chatdiet.barcode;

/**
 * Outcome of resolving a decoded UPC to a product identity.
 *
 * @param resolvedName     the product's name, if resolved locally, via Open Food Facts, or via FDC
 *                         Branded; {@code null} if {@code needsManualEntry}
 * @param needsManualEntry {@code true} if no source resolved the barcode - the manual-entry modal
 *                         is the terminus
 * @param wasNew           {@code true} if this call is what added the {@code FoodItem} to the
 *                         cache (an Open Food Facts or FDC hit); {@code false} for a cache hit that
 *                         already existed, or when {@code needsManualEntry}
 * @param foodItemId       id of the resolved cached item, so the quantity prompt can log by id
 *                         (immune to alias collisions); {@code null} if {@code needsManualEntry}
 * @param typicalServingG  the item's typical serving in grams for the prompt's servings hint, or
 *                         {@code null} if unknown or unresolved
 */
public record UpcResolveResult(String resolvedName, boolean needsManualEntry, boolean wasNew,
                                Long foodItemId, Double typicalServingG) {
}