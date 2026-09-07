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
 */
public record UpcResolveResult(String resolvedName, boolean needsManualEntry, boolean wasNew) {
}
