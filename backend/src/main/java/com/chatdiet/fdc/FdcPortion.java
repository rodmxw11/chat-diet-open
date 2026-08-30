package com.chatdiet.fdc;

/**
 * A normalized, application-facing unit-to-grams mapping from FDC's {@code foodPortions}, e.g.
 * ("medium", 118.0) for a banana.
 */
public record FdcPortion(String unitName, double grams) {
}
