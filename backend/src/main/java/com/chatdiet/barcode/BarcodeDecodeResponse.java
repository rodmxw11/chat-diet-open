package com.chatdiet.barcode;

/** Response DTO for {@link BarcodeController#decode}, carrying the decoded barcode value. */
public record BarcodeDecodeResponse(String upc) {
}
