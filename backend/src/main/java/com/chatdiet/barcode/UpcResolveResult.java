package com.chatdiet.barcode;

/**
 * Outcome of resolving a decoded UPC to a product identity.
 *
 * @param resolvedName    the product's name, if resolved locally, via Open Food Facts, or via FDC
 *                         Branded; {@code null} if {@code needsManualEntry}
 * @param needsManualEntry {@code true} if no source resolved the barcode - the manual-entry modal
 *                         is the terminus
 */
public record UpcResolveResult(String resolvedName, boolean needsManualEntry) {
}
